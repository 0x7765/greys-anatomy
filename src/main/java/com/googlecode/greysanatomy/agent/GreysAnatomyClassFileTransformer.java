package com.googlecode.greysanatomy.agent;

import com.googlecode.greysanatomy.console.command.Command.Info;
import com.googlecode.greysanatomy.probe.JobListener;
import com.googlecode.greysanatomy.probe.ProbeJobs;
import com.googlecode.greysanatomy.probe.Probes;
import com.googlecode.greysanatomy.util.GaReflectUtils;
import com.googlecode.greysanatomy.util.SearchUtils;
import com.googlecode.greysanatomy.util.WildcardUtils;
import javassist.*;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.lang.instrument.Instrumentation;
import java.lang.instrument.UnmodifiableClassException;
import java.security.ProtectionDomain;
import java.util.*;

import static com.googlecode.greysanatomy.probe.ProbeJobs.register;
import static com.googlecode.greysanatomy.util.LogUtils.*;
import static java.lang.System.arraycopy;

public class GreysAnatomyClassFileTransformer implements ClassFileTransformer {

    private final String prefMthWildcard;
    private final int id;
    private final List<CtBehavior> modifiedBehaviors;

    /*
     * ��֮ǰ���������һ������
     */
    private final static Map<Class<?>, byte[]> classBytesCache = new WeakHashMap<Class<?>, byte[]>();

    private GreysAnatomyClassFileTransformer(
            final String prefMthWildcard,
            final JobListener listener,
            final List<CtBehavior> modifiedBehaviors,
            final Info info) {
        this.prefMthWildcard = prefMthWildcard;
        this.modifiedBehaviors = modifiedBehaviors;
        this.id = info.getJobId();
        register(this.id, listener);
    }

    @Override
    public byte[] transform(final ClassLoader loader, String classNameForFilePath,
                            Class<?> classBeingRedefined, ProtectionDomain protectionDomain,
                            byte[] classFileBuffer)
            throws IllegalClassFormatException {

        final String className = GaReflectUtils.toClassPath(classNameForFilePath);

        // ������һ���������ƣ���ֹ���߲���������б��룬Ӱ�컺��
        synchronized (classBytesCache) {
            final ClassPool cp = new ClassPool(null);

//            final String cacheKey = className + "@" + loader;
            if (classBytesCache.containsKey(classBeingRedefined)) {
                // ���ȼ�1:
                // �����ֽ��뻺�棬���޴ӻ������
                // �ֽ��뻺������Ҫ������������������jobͬʱ��Ⱦ��һ��Class��
                cp.appendClassPath(new ByteArrayClassPath(className, classBytesCache.get(classBeingRedefined)));
            }

            // ���ȼ�2: ʹ��ClassLoader����
            cp.appendClassPath(new LoaderClassPath(loader));

            //���ȼ�3:
            // ����$Proxy֮��ʹ��JDKProxy��̬���ɵ��࣬����û��CodeSource�������޷�ͨ��ClassLoader.getResources()
            // ��ɶ��ֽ���Ļ�ȡ��ֻ��ʹ����transform�����classFileBuffer��ԭJVM�ֽ��룩�������Ⱦ����
            cp.appendClassPath(new ByteArrayClassPath(className, classFileBuffer));

            CtClass cc = null;
            byte[] data;
            try {
                cc = cp.getCtClass(className);
                cc.defrost();

                final CtBehavior[] cbs = cc.getDeclaredBehaviors();
                if (null != cbs) {
                    for (CtBehavior cb : cbs) {
                        if (/*cb.getMethodInfo().getName().matches(prefMthWildcard)*/WildcardUtils.match(cb.getMethodInfo().getName(), prefMthWildcard)) {
                            modifiedBehaviors.add(cb);
                            Probes.mine(id, cc, cb);
                        }

                        //  ��������ƥ��������ʽ
                        else {
                            debug("class=%s;method=%s was not matches wildcard=%s", className, cb.getMethodInfo().getName(), prefMthWildcard);
                        }
                    }
                }

                data = cc.toBytecode();
            } catch (Exception e) {
                debug(e, "transform class failed. class=%s, ClassLoader=%s",
                        className, loader);
                info("transform class failed. class=%s, ClassLoader=%s", className, loader);
                data = null;
            } finally {
                if (null != cc) {
                    cc.freeze();
                }
            }

            classBytesCache.put(classBeingRedefined, data);
            return data;
        }

    }


    /**
     * ��Ⱦ���
     *
     * @author vlinux
     */
    public static class TransformResult {

        private final int id;
        private final List<Class<?>> modifiedClasses;
        private final List<CtBehavior> modifiedBehaviors;

        private TransformResult(int id, final Collection<Class<?>> modifiedClasses, final List<CtBehavior> modifiedBehaviors) {
            this.id = id;
            this.modifiedClasses = new ArrayList<Class<?>>(modifiedClasses);
            this.modifiedBehaviors = new ArrayList<CtBehavior>(modifiedBehaviors);
        }

        public List<Class<?>> getModifiedClasses() {
            return modifiedClasses;
        }

        public List<CtBehavior> getModifiedBehaviors() {
            return modifiedBehaviors;
        }

        public int getId() {
            return id;
        }

    }

    /**
     * ����
     */
    public static interface Progress {

        void progress(int index, int total);

    }

    public static TransformResult transform(final Instrumentation instrumentation,
                                            final String prefClzWildcard,
                                            final String prefMthWildcard,
                                            final JobListener listener,
                                            final Info info,
                                            final boolean isForEach) throws UnmodifiableClassException {
        return transform(instrumentation, prefClzWildcard, prefMthWildcard, listener, info, isForEach, null);
    }

    /**
     * ��������α�
     *
     * @param instrumentation instrumentation
     * @param prefClzWildcard ������������ʽ
     * @param prefMthWildcard ������������ʽ
     * @param listener        ���������
     * @return ��Ⱦ���
     * @throws UnmodifiableClassException
     */
    public static TransformResult transform(final Instrumentation instrumentation,
                                            final String prefClzWildcard,
                                            final String prefMthWildcard,
                                            final JobListener listener,
                                            final Info info,
                                            final boolean isForEach,
                                            final Progress progress) throws UnmodifiableClassException {

        final List<CtBehavior> modifiedBehaviors = new ArrayList<CtBehavior>();
        final GreysAnatomyClassFileTransformer transformer
                = new GreysAnatomyClassFileTransformer(prefMthWildcard, listener, modifiedBehaviors, info);
        instrumentation.addTransformer(transformer, true);

        final Collection<Class<?>> modifiedClasses =
                //classesWildcardMatch(instrumentation, prefClzWildcard);
                SearchUtils.searchClassBySupers(instrumentation, SearchUtils.searchClassByClassWildcard(instrumentation, prefClzWildcard));
        synchronized (GreysAnatomyClassFileTransformer.class) {
            try {

                if (isForEach) {
                    forEachReTransformClasses(instrumentation, modifiedClasses, info, progress);
                } else {
                    batchReTransformClasses(instrumentation, modifiedClasses, info, progress);
                }

            } finally {
                instrumentation.removeTransformer(transformer);
            }//try
        }//sync

        return new TransformResult(transformer.id, modifiedClasses, modifiedBehaviors);

    }


    /**
     * �������������Ⱦ
     *
     * @param instrumentation instrumentation
     * @param modifiedClasses ��Ҫ��Ⱦ����
     * @param info            ������
     * @param progress        ������
     */
    private static void batchReTransformClasses(final Instrumentation instrumentation,
                                                final Collection<Class<?>> modifiedClasses,
                                                final Info info,
                                                final Progress progress) {
        if (ProbeJobs.isJobKilled(info.getJobId())) {
            info("job[id=%s] was killed, stop this reTransform.", info.getJobId());
            return;
        }

        final int size = modifiedClasses.size();
        final Class<?>[] classArray = new Class<?>[size];
        final Object[] objectArray = modifiedClasses.toArray();
        arraycopy(objectArray, 0, classArray, 0, size);

        try {
            instrumentation.retransformClasses(classArray);
            info("reTransformClasses:size=[%s]", size);
            debug("reTransformClasses:%s", modifiedClasses);
        } catch (Throwable t) {
            debug(t, "reTransformClasses failed, classes=%s.", modifiedClasses);
            warn("reTransformClasses failed, size=%s.", size);
        } finally {
            if (null != progress) {
                progress.progress(size, size);
            }
        }//try
    }

    /**
     * forEach�������������Ⱦ
     *
     * @param instrumentation instrumentation
     * @param modifiedClasses ��Ҫ��Ⱦ����
     * @param info            ������
     * @param progress        ������
     */
    private static void forEachReTransformClasses(final Instrumentation instrumentation,
                                                  final Collection<Class<?>> modifiedClasses,
                                                  final Info info,
                                                  final Progress progress) {

        int index = 0;
        int total = modifiedClasses.size();

        for (final Class<?> clazz : modifiedClasses) {
            if (ProbeJobs.isJobKilled(info.getJobId())) {
                info("job[id=%s] was killed, stop this reTransform.", info.getJobId());
                break;
            }
            try {
                instrumentation.retransformClasses(clazz);
                debug("reTransformClasses, index=%s;total=%s;class=%s;", index, total, clazz);
            } catch (Throwable t) {
                debug(t, "reTransformClasses failed, index=%s;total=%s;class=%s;", index, total, clazz);
                warn("reTransformClasses failed, index=%s;total=%s;class=%s;", index, total, clazz);
            } finally {
                if (null != progress) {
                    progress.progress(++index, total);
                }
            }//try
        }//for

    }

}
