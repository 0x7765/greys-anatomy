package com.github.ompc.greys.advisor;

import com.github.ompc.greys.command.affect.EnhancerAffect;
import com.github.ompc.greys.util.LogUtil;
import com.github.ompc.greys.util.Matcher;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.AdviceAdapter;
import org.objectweb.asm.commons.Method;

import java.util.Map;
import java.util.Stack;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import static com.github.ompc.greys.agent.AgentLauncher.*;
import static java.util.logging.Level.FINE;
import static java.util.logging.Level.WARNING;

/**
 * 通知编织者<br/>
 * <p/>
 * <h2>线程帧栈与执行帧栈</h2>
 * 编织者在执行通知的时候有两个重要的栈:线程帧栈(threadFrameStack),执行帧栈(frameStack)
 * <p/>
 * Created by vlinux on 15/5/17.
 */
public class AdviceWeaver extends ClassVisitor implements Opcodes {

    private final static Logger logger = LogUtil.getLogger();

    // 通知监听器集合
    private final static Map<Integer/*ADVICE_ID*/, AdviceListener> advices
            = new ConcurrentHashMap<Integer, AdviceListener>();

    // 线程帧封装
    private static final Map<Thread, Stack<Stack<Object>>> threadBoundContexts
            = new ConcurrentHashMap<Thread, Stack<Stack<Object>>>();


    /**
     * 方法开始<br/>
     * 用于编织通知器,外部不会直接调用
     *
     * @param adviceId   通知ID
     * @param className  类名
     * @param methodName 方法名
     * @param methodDesc 方法描述
     * @param target     返回结果
     *                   若为无返回值方法(void),则为null
     * @param args       参数列表
     */
    public static void methodOnBegin(
            int adviceId,
            String className, String methodName, String methodDesc,
            Object target, Object[] args) {

        // 构建执行帧栈,保护当前的执行现场
        final Stack<Object> frameStack = new Stack<Object>();
        frameStack.push(className);
        frameStack.push(methodName);
        frameStack.push(methodDesc);
        frameStack.push(target);
        frameStack.push(args);

        // 获取通知器并做前置通知
        final AdviceListener listener = advices.get(adviceId);
        before(listener, className, methodName, methodDesc, target, args);

        // 通知器也通过执行帧栈保存,到End环节恢复
        // 避免到End时再次通过advices获取,减少性能开销
        frameStack.push(listener);

        // 保护当前执行帧栈,压入线程帧栈
        threadFrameStackPush(frameStack);
    }


    /**
     * 方法以返回结束<br/>
     * 用于编织通知器,外部不会直接调用
     *
     * @param returnObject 返回对象
     *                     若目标为静态方法,则为null
     */
    public static void methodOnReturnEnd(Object returnObject) {
        methodOnEnd(false, returnObject);
    }

    /**
     * 方法以抛异常结束<br/>
     * 用于编织通知器,外部不会直接调用
     *
     * @param throwable 抛出异常
     */
    public static void methodOnThrowingEnd(Throwable throwable) {
        methodOnEnd(true, throwable);
    }

    /**
     * 所有的返回都统一处理
     *
     * @param isThrowing        标记正常返回结束还是抛出异常结束
     * @param returnOrThrowable 正常返回或者抛出异常对象
     */
    private static void methodOnEnd(boolean isThrowing, Object returnOrThrowable) {

        // 弹射线程帧栈,恢复Begin所保护的执行帧栈
        final Stack<Object> frameStack = threadFrameStackPop();

        // 弹射执行帧栈,恢复Begin所保护的现场
        // 当然了，这里弹射的是对象的引用，并不能真正像寄存器那样恢复真正的值
        final AdviceListener listener = (AdviceListener) frameStack.pop();
        final Object[] args = (Object[]) frameStack.pop();
        final Object target = frameStack.pop();
        final String methodDesc = (String) frameStack.pop();
        final String methodName = (String) frameStack.pop();
        final String className = (String) frameStack.pop();

        // 异常通知
        if (isThrowing) {
            afterThrowing(listener, className, methodName, methodDesc, target, args, (Throwable) returnOrThrowable);
        }

        // 返回通知
        else {
            afterReturning(listener, className, methodName, methodDesc, target, args, returnOrThrowable);
        }

    }

    /*
     * 线程帧栈压栈<br/>
     * 将当前执行帧栈压入线程栈
     */
    private static void threadFrameStackPush(Stack<Object> frameStack) {
        final Thread thread = Thread.currentThread();
        Stack<Stack<Object>> threadFrameStack = threadBoundContexts.get(thread);
        if (null == threadFrameStack) {
            threadBoundContexts.put(thread, threadFrameStack = new Stack<Stack<Object>>());
        }
        threadFrameStack.push(frameStack);
    }

    private static Stack<Object> threadFrameStackPop() {
        return threadBoundContexts.get(Thread.currentThread()).pop();
    }


    /**
     * 注册监听器
     *
     * @param adviceId 通知ID
     * @param listener 通知监听器
     */
    public static void reg(int adviceId, AdviceListener listener) {

        // 触发监听器创建
        listener.create();

        // 注册监听器
        advices.put(adviceId, listener);
    }

    /**
     * 注销监听器
     *
     * @param adviceId 通知ID
     */
    public static void unReg(int adviceId) {

        // 注销监听器
        final AdviceListener listener = advices.remove(adviceId);

        // 触发监听器销毁
        if (null != listener) {
            listener.destroy();
        }

    }

    /**
     * 判断通知是否已经被注册
     *
     * @param adviceId 通知ID
     * @return true : 已被注册 / false : 未被注册
     */
    public static boolean isReg(int adviceId) {
        return advices.containsKey(adviceId);
    }


    private static void before(AdviceListener listener,
                               String className, String methodName, String methodDesc,
                               Object target, Object[] args) {

        if (null != listener) {
            try {
                listener.before(className, methodName, methodDesc, target, args);
            } catch (Throwable t) {
                final String message = String.format("advice before : %s", t.getMessage());
                if (logger.isLoggable(WARNING)) {
                    logger.log(WARNING, message);
                } else if (logger.isLoggable(FINE)) {
                    logger.log(FINE, message, t);
                }
            }
        }

    }

    private static void afterReturning(AdviceListener listener,
                                       String className, String methodName, String methodDesc,
                                       Object target, Object[] args, Object returnObject) {
        if (null != listener) {
            try {
                listener.afterReturning(className, methodName, methodDesc, target, args, returnObject);
            } catch (Throwable t) {
                final String message = String.format("advice before : %s", t.getMessage());
                if (logger.isLoggable(WARNING)) {
                    logger.log(WARNING, message);
                } else if (logger.isLoggable(FINE)) {
                    logger.log(FINE, message, t);
                }
            }
        }
    }

    private static void afterThrowing(AdviceListener listener,
                                      String className, String methodName, String methodDesc,
                                      Object target, Object[] args, Throwable throwable) {
        if (null != listener) {
            try {
                listener.afterThrowing(className, methodName, methodDesc, target, args, throwable);
            } catch (Throwable t) {
                final String message = String.format("advice afterThrowing : %s", t.getMessage());
                if (logger.isLoggable(WARNING)) {
                    logger.log(WARNING, message);
                } else if (logger.isLoggable(FINE)) {
                    logger.log(FINE, message, t);
                }
            }
        }
    }


    private final int adviceId;
    private final String className;
    private final Matcher matcher;
    private final EnhancerAffect affect;

    /**
     * 构建通知编织器
     *
     * @param adviceId  通知ID
     * @param className 类名称
     * @param matcher   方法匹配
     *                  只有匹配上的方法才会被织入通知器
     * @param affect    影响计数
     * @param cv        ClassVisitor for ASM
     */
    public AdviceWeaver(int adviceId, String className, Matcher matcher, EnhancerAffect affect, ClassVisitor cv) {
        super(ASM5, cv);
        this.adviceId = adviceId;
        this.className = className;
        this.matcher = matcher;
        this.affect = affect;
    }

    /**
     * 是否抽象属性
     *
     * @param access 属性值
     * @return true : 抽象 / false : 非抽象
     */
    private boolean isAbstract(int access) {
        return (ACC_ABSTRACT & access) == ACC_ABSTRACT;
    }


    /**
     * 是否需要忽略
     */
    private boolean isIgnore(MethodVisitor mv, int access, String methodName) {
        return null == mv
                || isAbstract(access)
                || !matcher.matching(methodName);
    }

    @Override
    public MethodVisitor visitMethod(
            final int access,
            final String name,
            final String desc,
            final String signature,
            final String[] exceptions) {

        final MethodVisitor mv = super.visitMethod(access, name, desc, signature, exceptions);

        if (isIgnore(mv, access, name)) {
            return mv;
        }

        // 编织方法计数
        affect.mCnt(1);

        return new AdviceAdapter(ASM5, mv, access, name, desc) {

            private final Label beginLabel = new Label();
            private final Label endLabel = new Label();
            private final Type ASM_TYPE_SYSTEM = Type.getType(java.lang.System.class);
            private final Type ASM_TYPE_METHOD = Type.getType(java.lang.reflect.Method.class);
            private final Type ASM_TYPE_PROPERTIES = Type.getType(java.util.Properties.class);
            private final Method ASM_METHOD_METHOD_INVOKE = Method.getMethod("Object invoke(Object,Object[])");


            /**
             * 加载通知方法
             * @param keyOfMethod 通知方法KEY
             *                    AgentLauncher.KEY_GREYS_ADVICE_BEFORE_METHOD
             *                    AgentLauncher.KEY_GREYS_ADVICE_RETURN_METHOD
             *                    AgentLauncher.KEY_GREYS_ADVICE_THROWS_METHOD
             */
            private void loadAdviceMethod(String keyOfMethod) {
                invokeStatic(ASM_TYPE_SYSTEM, Method.getMethod("java.util.Properties getProperties()"));
                push(keyOfMethod);
                invokeVirtual(ASM_TYPE_PROPERTIES, Method.getMethod("Object get(Object)"));
                checkCast(ASM_TYPE_METHOD);
            }

            /**
             * 加载before通知参数数组
             */
            private void loadArrayForBefore() {
                push(6);
                newArray(Type.getType(Object.class));

                dup();
                push(0);
                push(adviceId);
                box(Type.getType(int.class));
                arrayStore(Type.getType(Integer.class));

                dup();
                push(1);
                push(className);
                arrayStore(Type.getType(String.class));

                dup();
                push(2);
                push(name);
                arrayStore(Type.getType(String.class));

                dup();
                push(3);
                push(desc);
                arrayStore(Type.getType(String.class));

                dup();
                push(4);
                loadThisOrPushNullIfIsStatic();
                arrayStore(Type.getType(Object.class));

                dup();
                push(5);
                loadArgArray();
                arrayStore(Type.getType(Object[].class));
            }


            @Override
            protected void onMethodEnter() {

                // 加载before方法
                loadAdviceMethod(KEY_GREYS_ADVICE_BEFORE_METHOD);

                // 推入Method.invoke()的第一个参数
                pushNull();

                // 方法参数
                loadArrayForBefore();

                // 调用方法
                invokeVirtual(ASM_TYPE_METHOD, ASM_METHOD_METHOD_INVOKE);
                pop();

                mark(beginLabel);

            }


            /*
             * 加载return通知参数数组
             */
            private void loadReturnArgs() {
                dup2X1();
                pop2();
                push(1);
                newArray(Type.getType(Object.class));
                dup();
                dup2X1();
                pop2();
                push(0);
                swap();
                arrayStore(Type.getType(Object.class));
            }

            @Override
            protected void onMethodExit(int opcode) {

                if (!isThrow(opcode)) {

                    // 加载返回对象
                    loadReturn(opcode);

                    // 加载returning方法
                    loadAdviceMethod(KEY_GREYS_ADVICE_RETURN_METHOD);

                    // 推入Method.invoke()的第一个参数
                    pushNull();

                    // 加载return通知参数数组
                    loadReturnArgs();

                    invokeVirtual(ASM_TYPE_METHOD, ASM_METHOD_METHOD_INVOKE);
                    pop();

                }

            }


            /*
             * 创建throwing通知参数本地变量
             */
            private void loadThrowArgs() {
                dup2X1();
                pop2();
                push(1);
                newArray(Type.getType(Object.class));
                dup();
                dup2X1();
                pop2();
                push(0);
                swap();
                arrayStore(Type.getType(Throwable.class));
            }

            @Override
            public void visitMaxs(int maxStack, int maxLocals) {

                mark(endLabel);
                catchException(beginLabel, endLabel, null);

                // 加载异常
                loadThrow();

                // 加载throwing方法
                loadAdviceMethod(KEY_GREYS_ADVICE_THROWS_METHOD);

                // 推入Method.invoke()的第一个参数
                pushNull();

                // 加载throw通知参数数组
                loadThrowArgs();

                // 调用方法
                invokeVirtual(ASM_TYPE_METHOD, ASM_METHOD_METHOD_INVOKE);
                pop();

                throwException();
                super.visitMaxs(maxStack, maxLocals);
            }

            /**
             * 是否静态方法
             * @return true:静态方法 / false:非静态方法
             */
            private boolean isStaticMethod() {
                return (methodAccess & ACC_STATIC) != 0;
            }

            /**
             * 是否抛出异常返回(通过字节码判断)
             * @param opcode 操作码
             * @return true:以抛异常形式返回 / false:非抛异常形式返回(return)
             */
            private boolean isThrow(int opcode) {
                return opcode == ATHROW;
            }

            /**
             * 将NULL推入堆栈
             */
            private void pushNull() {
                push((Type) null);
            }

            /**
             * 加载this/null
             */
            private void loadThisOrPushNullIfIsStatic() {
                if (isStaticMethod()) {
                    pushNull();
                } else {
                    loadThis();
                }
            }

            /**
             * 加载返回值
             * @param opcode 操作吗
             */
            private void loadReturn(int opcode) {
                switch (opcode) {

                    case RETURN: {
                        pushNull();
                        break;
                    }

                    case ARETURN: {
                        dup();
                        break;
                    }

                    case LRETURN:
                    case DRETURN: {
                        dup2();
                        box(Type.getReturnType(methodDesc));
                        break;
                    }

                    default: {
                        dup();
                        box(Type.getReturnType(methodDesc));
                        break;
                    }

                }
            }

            /**
             * 加载异常
             */
            private void loadThrow() {
                dup();
            }

        };

    }


}
