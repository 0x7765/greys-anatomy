package com.github.ompc.greys.core.command;

import com.github.ompc.greys.core.advisor.AdviceListener;
import com.github.ompc.greys.core.advisor.ReflectAdviceTracingListenerAdapter;
import com.github.ompc.greys.core.command.annotation.Cmd;
import com.github.ompc.greys.core.command.annotation.IndexArg;
import com.github.ompc.greys.core.command.annotation.NamedArg;
import com.github.ompc.greys.core.exception.ExpressException;
import com.github.ompc.greys.core.server.Session;
import com.github.ompc.greys.core.util.Advice;
import com.github.ompc.greys.core.util.GaMethod;
import com.github.ompc.greys.core.util.Matcher;
import com.github.ompc.greys.core.view.TreeView;

import java.lang.instrument.Instrumentation;
import java.util.concurrent.atomic.AtomicInteger;

import static com.github.ompc.greys.core.util.Advice.newForAfterRetuning;
import static com.github.ompc.greys.core.util.Advice.newForAfterThrowing;
import static com.github.ompc.greys.core.util.Express.ExpressFactory.newExpress;
import static com.github.ompc.greys.core.util.GaStringUtils.tranClassName;
import static org.apache.commons.lang3.StringUtils.isBlank;

/**
 * 调用跟踪命令<br/>
 * 负责输出一个类中的所有方法调用路径 Created by vlinux on 15/5/27.
 */
@Cmd(name = "trace", sort = 6, summary = "Display the detailed thread stack of specified class and method",
        eg = {
            "trace -E org\\.apache\\.commons\\.lang\\.StringUtils isBlank",
            "trace org.apache.commons.lang.StringUtils isBlank",
            "trace *StringUtils isBlank",
            "trace *StringUtils isBlank params[0].length==1"
        })
public class TraceCommand implements Command {

    @IndexArg(index = 0, name = "class-pattern", summary = "Path and classname of Pattern Matching")
    private String classPattern;

    @IndexArg(index = 1, name = "method-pattern", summary = "Method of Pattern Matching")
    private String methodPattern;

    @IndexArg(index = 2, name = "condition-express", isRequired = false,
            summary = "Conditional expression by groovy",
            description = "" +
                    "For example\n" +
                    "\n" +
                    "    TRUE  : 1==1\n" +
                    "    TRUE  : true\n" +
                    "    FALSE : false\n" +
                    "    TRUE  : params.length>=0\n" +
                    "    FALSE : 1==2\n" +
                    "\n" +
                    "The structure\n" +
                    "\n" +
                    "          target : the object \n" +
                    "           clazz : the object's class\n" +
                    "          method : the constructor or method\n" +
                    "    params[0..n] : the parameters of method\n" +
                    "       returnObj : the returned object of method\n" +
                    "        throwExp : the throw exception of method\n" +
                    "        isReturn : the method ended by return\n" +
                    "         isThrow : the method ended by throwing exception"
    )
    private String conditionExpress;

    @NamedArg(name = "E", summary = "Enable regular expression to match (wildcard matching by default)")
    private boolean isRegEx = false;

    @NamedArg(name = "n", hasValue = true, summary = "Threshold of execution times")
    private Integer numberOfLimit;
    
    @Override
    public Action getAction() {

        final Matcher classNameMatcher = isRegEx
                ? new Matcher.RegexMatcher(classPattern)
                : new Matcher.WildcardMatcher(classPattern);

        final Matcher methodNameMatcher = isRegEx
                ? new Matcher.RegexMatcher(methodPattern)
                : new Matcher.WildcardMatcher(methodPattern);

        return new GetEnhancerAction() {

            @Override
            public GetEnhancer action(Session session, Instrumentation inst, final Printer printer) throws Throwable {
                return new GetEnhancer() {

                    private final AtomicInteger times = new AtomicInteger();
                    
                    @Override
                    public Matcher getClassNameMatcher() {
                        return classNameMatcher;
                    }

                    @Override
                    public Matcher getMethodNameMatcher() {
                        return methodNameMatcher;
                    }

                    @Override
                    public AdviceListener getAdviceListener() {
                        return new ReflectAdviceTracingListenerAdapter() {

                            private final ThreadLocal<Entity> threadBoundEntity = new ThreadLocal<Entity>() {

                                @Override
                                protected Entity initialValue() {
                                    final Entity e = new Entity();
                                    e.view = createTreeView();
                                    e.deep = 0;
                                    return e;
                                }

                            };

                            @Override
                            public void create() {

                            }

                            @Override
                            public void destroy() {
                                threadBoundEntity.remove();
                            }

                            @Override
                            public void invokeBeforeTracing(
                                    String tracingClassName,
                                    String tracingMethodName,
                                    String tracingMethodDesc) throws Throwable {
                                threadBoundEntity.get().view.begin(tranClassName(tracingClassName) + ":" + tracingMethodName + "()");
                            }

                            @Override
                            public void invokeAfterTracing(
                                    String tracingClassName,
                                    String tracingMethodName,
                                    String tracingMethodDesc) throws Throwable {
                                threadBoundEntity.get().view.end();
                            }

                            @Override
                            public void before(
                                    ClassLoader loader, 
                                    Class<?> clazz, 
                                    GaMethod method, 
                                    Object target, 
                                    Object[] args) throws Throwable {
                                threadBoundEntity.get().view.begin(clazz.getName() + ":" + method.getName() + "()");
                                threadBoundEntity.get().deep++;
                            }

                            @Override
                            public void afterReturning(
                                    ClassLoader loader, 
                                    Class<?> clazz, 
                                    GaMethod method, 
                                    Object target, 
                                    Object[] args, 
                                    Object returnObject) throws Throwable {
                                threadBoundEntity.get().view.end();
                                final Advice advice = newForAfterRetuning(loader, clazz, method, target, args, returnObject);
                                finishing(advice);
                            }

                            @Override
                            public void afterThrowing(
                                    ClassLoader loader, 
                                    Class<?> clazz, 
                                    GaMethod method, 
                                    Object target, 
                                    Object[] args, 
                                    Throwable throwable) throws Throwable {
                                threadBoundEntity.get().view.begin("throw:" + throwable.getClass().getName() + "()").end().end();
                                final Advice advice = newForAfterThrowing(loader, clazz, method, target, args, throwable);
                                finishing(advice);
                            }

                            private boolean isPrintIfNecessary(Advice advice) {
                                try {
                                    return isBlank(conditionExpress)
                                            || newExpress(advice).is(conditionExpress);
                                } catch (ExpressException e) {
                                    return false;
                                }
                            }
                            
                            private boolean isLimited(int currentTimes) {
                                return null != numberOfLimit
                                        && currentTimes >= numberOfLimit;
                            }

                            private void finishing(Advice advice) {
                                if (--threadBoundEntity.get().deep == 0) {
                                    
                                    if (isPrintIfNecessary(advice)) {
                                        final boolean isF = isLimited(times.incrementAndGet());
                                        printer.println(isF, threadBoundEntity.get().view.draw());
                                    }
                                    threadBoundEntity.remove();
                                }
                            }

                            private TreeView createTreeView() {
                                return new TreeView(true, "Tracing...");
                            }

                        };
                    }
                };
            }

        };

    }

    /**
     * 用于在ThreadLocal中传递的实体
     */
    private static class Entity {

        TreeView view;
        int deep;

    }

}
