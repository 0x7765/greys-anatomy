package com.googlecode.greysanatomy.probe;

import com.googlecode.greysanatomy.probe.Advice.Target;
import javassist.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.googlecode.greysanatomy.probe.ProbeJobs.getJobListeners;
import static com.googlecode.greysanatomy.probe.ProbeJobs.isListener;
import static java.lang.String.format;
import static javassist.Modifier.*;

/**
 * ̽��㴥����<br/>
 * ����ĵ��У�һ����4��̽��㣬���Ƿֱ��Ӧ<br/>
 * fucntion f()
 * {
 * // probe:_before()
 * try {
 * do something...
 * // probe:_success()
 * } catch(Throwable t) {
 * // probe:_throws();
 * throw t;
 * } finally {
 * // probe:_finish();
 * }
 * <p/>
 * }
 *
 * @author vlinux
 */
public class Probes {

    private static final Logger logger = LoggerFactory.getLogger("greysanatomy");

    private static final String jobsClass = "com.googlecode.greysanatomy.probe.ProbeJobs";
    private static final String probesClass = "com.googlecode.greysanatomy.probe.Probes";

    private static Target newTarget(String targetClassName, String targetBehaviorName, Object targetThis, Class targetClass, Class[] parameterTypes) {
        return new Target(targetClassName, targetBehaviorName, targetThis, targetClass, parameterTypes);
    }

    public static void doBefore(int id, String targetClassName, String targetBehaviorName, Object targetThis, Object[] args, Class targetClass, Class[] parameterTypes) {
        if (isListener(id, AdviceListener.class)) {
            try {
                Advice p = new Advice(newTarget(targetClassName, targetBehaviorName, targetThis, targetClass, parameterTypes), args, false);
                ((AdviceListener) getJobListeners(id)).onBefore(p);
            } catch (Throwable t) {
                logger.warn("error at doBefore", t);
            }
        }
    }

    public static void doSuccess(int id, String targetClassName, String targetBehaviorName, Object targetThis, Object[] args, Object returnObj, Class targetClass, Class[] parameterTypes) {
        if (isListener(id, AdviceListener.class)) {
            try {
                Advice p = new Advice(newTarget(targetClassName, targetBehaviorName, targetThis, targetClass, parameterTypes), args, false);
                p.setReturnObj(returnObj);
                ((AdviceListener) getJobListeners(id)).onSuccess(p);
            } catch (Throwable t) {
                logger.warn("error at onSuccess", t);
            }
            doFinish(id, targetClassName, targetBehaviorName, targetThis, args, returnObj, null, targetClass, parameterTypes);
        }

    }

    public static void doException(int id, String targetClassName, String targetBehaviorName, Object targetThis, Object[] args, Throwable throwException, Class targetClass, Class[] parameterTypes) {
        if (isListener(id, AdviceListener.class)) {
            try {
                Advice p = new Advice(newTarget(targetClassName, targetBehaviorName, targetThis, targetClass, parameterTypes), args, false);
                p.setThrowException(throwException);
                ((AdviceListener) getJobListeners(id)).onException(p);
            } catch (Throwable t) {
                logger.warn("error at onException", t);
            }
            doFinish(id, targetClassName, targetBehaviorName, targetThis, args, null, throwException, targetClass, parameterTypes);
        }

    }

    public static void doFinish(int id, String targetClassName, String targetBehaviorName, Object targetThis, Object[] args, Object returnObj, Throwable throwException, Class targetClass, Class[] parameterTypes) {
        if (isListener(id, AdviceListener.class)) {
            try {
                Advice p = new Advice(newTarget(targetClassName, targetBehaviorName, targetThis, targetClass, parameterTypes), args, true);
                p.setThrowException(throwException);
                p.setReturnObj(returnObj);
                ((AdviceListener) getJobListeners(id)).onFinish(p);
            } catch (Throwable t) {
                logger.warn("error at onFinish", t);
            }
        }
    }


    /**
     * �Ƿ���˵���ǰ̽���Ŀ��
     *
     * @param cc
     * @param cb
     * @return
     */
    private static boolean isIngore(CtClass cc, CtBehavior cb) {

        final int ccMod = cc.getModifiers();
        final int cbMod = cb.getModifiers();

        if (isInterface(ccMod)
                || isAbstract(cbMod)
                || cc.getName().startsWith("com.googlecode.greysanatomy.")) {
            return true;
        }

        return false;

    }


    /**
     * ���̽����
     *
     * @param id
     * @param cc
     * @param cb
     * @throws CannotCompileException
     * @throws NotFoundException
     */
    public static void mine(int id, CtClass cc, CtBehavior cb) throws CannotCompileException, NotFoundException {

        if (isIngore(cc, cb)) {
            return;
        }

        // Ŀ��ʵ��,����Ǿ�̬��������Ϊnull
        final String javassistThis = isStatic(cb.getModifiers()) ? "null" : "this";

        // ���֪ͨ
        if (isListener(id, AdviceListener.class)) {
            // ���캯���������ǲ�����insertBefore��,���Թ��캯����before������doCache��
            if (cb.getMethodInfo().isMethod()) {
                mineProbeForMethod(cb, id, cc.getName(), cb.getName(), javassistThis);
            } else if (cb.getMethodInfo().isConstructor()) {
                mineProbeForConstructor(cb, id, cc.getName(), cb.getName(), javassistThis);
            }
        }

    }

    private static void mineProbeForConstructor(CtBehavior cb, int id, String targetClassName, String targetBehaviorName, String javassistThis) throws CannotCompileException, NotFoundException {
        cb.addCatch(format("{if(%s.isJobAlive(%s)){%s.doBefore(%s,\"%s\",\"%s\",%s,$args,$class,$sig);%s.doException(%s,\"%s\",\"%s\",%s,$args,$e,$class,$sig);}throw $e;}",
                        jobsClass, id,
                        probesClass, id, targetClassName, targetBehaviorName, javassistThis,
                        probesClass, id, targetClassName, targetBehaviorName, javassistThis),
                ClassPool.getDefault().get("java.lang.Throwable"));

        // TODO : ��֣�Ϊɶ����ҪdoBefore����?
        cb.insertAfter(format("{if(%s.isJobAlive(%s)){%s.doBefore(%s,\"%s\",\"%s\",%s,$args,$class,$sig);%s.doSuccess(%s,\"%s\",\"%s\",%s,$args,($w)$_,$class,$sig);}}",
                jobsClass, id,
                probesClass, id, targetClassName, targetBehaviorName, javassistThis,
                probesClass, id, targetClassName, targetBehaviorName, javassistThis));

    }

    private static void mineProbeForMethod(CtBehavior cb, int id, String targetClassName, String targetBehaviorName, String javassistThis) throws CannotCompileException, NotFoundException {

        cb.insertBefore(format("{if(%s.isJobAlive(%s))%s.doBefore(%s,\"%s\",\"%s\",%s,$args,$class,$sig);}",
                jobsClass, id,
                probesClass, id, targetClassName, targetBehaviorName, javassistThis));

        cb.addCatch(format("{if(%s.isJobAlive(%s))%s.doException(%s,\"%s\",\"%s\",%s,$args,$e,$class,$sig);throw $e;}",
                        jobsClass, id,
                        probesClass, id, targetClassName, targetBehaviorName, javassistThis),
                ClassPool.getDefault().get("java.lang.Throwable"));

        cb.insertAfter(format("{if(%s.isJobAlive(%s))%s.doSuccess(%s,\"%s\",\"%s\",%s,$args,($w)$_,$class,$sig);}",
                jobsClass, id,
                probesClass, id, targetClassName, targetBehaviorName, javassistThis));
    }

}
