package com.googlecode.greysanatomy.probe;

import com.googlecode.greysanatomy.probe.Advice.Target;
import com.googlecode.greysanatomy.util.GaStringUtils;
import javassist.*;

import static com.googlecode.greysanatomy.probe.ProbeJobs.getJobListeners;
import static com.googlecode.greysanatomy.probe.ProbeJobs.isListener;
import static com.googlecode.greysanatomy.util.LogUtils.debug;
import static com.googlecode.greysanatomy.util.LogUtils.warn;
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

    private static final String jobsClass = "com.googlecode.greysanatomy.probe.ProbeJobs";
    private static final String probesClass = "com.googlecode.greysanatomy.probe.Probes";

    private static Target newTarget(String targetClassName, String targetBehaviorName, Object targetThis) {
        return new Target(targetClassName, targetBehaviorName, targetThis);
    }

    public static void doBefore(int id, String targetClassName, String targetBehaviorName, Object targetThis, Object[] args) {
        if (isListener(id, AdviceListener.class)) {
            try {
                Advice p = new Advice(newTarget(targetClassName, targetBehaviorName, targetThis), args, false);
                ((AdviceListener) getJobListeners(id)).onBefore(p);
            } catch (Throwable t) {
                warn(t, "error at doBefore.");
            }
        }
    }

    public static void doSuccess(int id, String targetClassName, String targetBehaviorName, Object targetThis, Object[] args, Object returnObj) {
        if (isListener(id, AdviceListener.class)) {
            try {
                Advice p = new Advice(newTarget(targetClassName, targetBehaviorName, targetThis), args, false);
                p.setReturnObj(returnObj);
                ((AdviceListener) getJobListeners(id)).onSuccess(p);
            } catch (Throwable t) {
                warn(t, "error at onSuccess.");
            }
            doFinish(id, targetClassName, targetBehaviorName, targetThis, args, returnObj, null);
        }

    }

    public static void doException(int id, String targetClassName, String targetBehaviorName, Object targetThis, Object[] args, Throwable throwException) {
        if (isListener(id, AdviceListener.class)) {
            try {
                Advice p = new Advice(newTarget(targetClassName, targetBehaviorName, targetThis), args, false);
                p.setThrowException(throwException);
                ((AdviceListener) getJobListeners(id)).onException(p);
            } catch (Throwable t) {
                warn(t, "error at onException.");
            }
            doFinish(id, targetClassName, targetBehaviorName, targetThis, args, null, throwException);
        }

    }

    public static void doFinish(int id, String targetClassName, String targetBehaviorName, Object targetThis, Object[] args, Object returnObj, Throwable throwException) {
        if (isListener(id, AdviceListener.class)) {
            try {
                Advice p = new Advice(newTarget(targetClassName, targetBehaviorName, targetThis), args, true);
                p.setThrowException(throwException);
                p.setReturnObj(returnObj);
                ((AdviceListener) getJobListeners(id)).onFinish(p);
            } catch (Throwable t) {
                warn(t, "error at onFinish.");
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
    private static boolean isIgnore(CtClass cc, CtBehavior cb) {

        final int ccMod = cc.getModifiers();
        final int cbMod = cb.getModifiers();

        final boolean isInterface = isInterface(ccMod);
        final boolean isAbstract = isAbstract(cbMod);

        if (isInterface
                || isAbstract
                || cc.getName().startsWith("com.googlecode.greysanatomy.")
                || cc.getName().startsWith("ognl.")) {
            debug("ignore class:%s;behavior=%s;isInterface=%s;isAbstract=%s;",
                    cc.getName(),
                    cb.getName(),
                    isInterface,
                    isAbstract);
            return true;
        }

        // ���˵�main����
        if( isStatic(cbMod)
                && isPublic(cbMod)
                && GaStringUtils.equals(cb.getName(),"main")) {
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

        if (isIgnore(cc, cb)) {
            return;
        }

        final boolean isStatic = isStatic(cb.getModifiers());
        // Ŀ��ʵ��,����Ǿ�̬��������Ϊnull
        final String javassistThis = isStatic ? "null" : "this";

        // ���֪ͨ
        if (isListener(id, AdviceListener.class)) {
            // ���캯���������ǲ�����insertBefore��,���Թ��캯����before������doCache��
            if (cb.getMethodInfo().isMethod()) {
                mineProbeForMethod(cb, id, cc.getName(), cb.getName(), javassistThis);
            } else if (cb.getMethodInfo().isConstructor()) {
                final String targetBehaviorName = isStatic ? "<cinit>" : "<init>";
                mineProbeForConstructor(cb, id, cc.getName(), targetBehaviorName, javassistThis);
            }
        }

    }


    private static void mineProbeForConstructor(CtBehavior cb, int id, String targetClassName, String targetBehaviorName, String javassistThis) throws CannotCompileException, NotFoundException {

        cb.addCatch(format("{if(%s.isJobAlive(%s)){%s.doBefore(%s,\"%s\",\"%s\",%s,$args);%s.doException(%s,\"%s\",\"%s\",%s,$args,$e);}throw $e;}",
                        jobsClass, id,
                        probesClass, id, targetClassName, targetBehaviorName, javassistThis,
                        probesClass, id, targetClassName, targetBehaviorName, javassistThis),
                ClassPool.getDefault().get("java.lang.Throwable"));

        cb.insertAfter(format("{if(%s.isJobAlive(%s)){%s.doBefore(%s,\"%s\",\"%s\",%s,$args);%s.doSuccess(%s,\"%s\",\"%s\",%s,$args,($w)$_);}}",
                jobsClass, id,
                probesClass, id, targetClassName, targetBehaviorName, javassistThis,
                probesClass, id, targetClassName, targetBehaviorName, javassistThis));

    }

    private static void mineProbeForMethod(CtBehavior cb, int id, String targetClassName, String targetBehaviorName, String javassistThis) throws CannotCompileException, NotFoundException {

        cb.insertBefore(format("{if(%s.isJobAlive(%s))%s.doBefore(%s,\"%s\",\"%s\",%s,$args);}",
                jobsClass, id,
                probesClass, id, targetClassName, targetBehaviorName, javassistThis));

        cb.addCatch(format("{if(%s.isJobAlive(%s))%s.doException(%s,\"%s\",\"%s\",%s,$args,$e);throw $e;}",
                        jobsClass, id,
                        probesClass, id, targetClassName, targetBehaviorName, javassistThis),
                ClassPool.getDefault().get("java.lang.Throwable"));

        cb.insertAfter(format("{if(%s.isJobAlive(%s))%s.doSuccess(%s,\"%s\",\"%s\",%s,$args,($w)$_);}",
                jobsClass, id,
                probesClass, id, targetClassName, targetBehaviorName, javassistThis));
    }

}
