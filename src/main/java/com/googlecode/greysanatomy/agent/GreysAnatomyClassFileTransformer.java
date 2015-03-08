package com.googlecode.greysanatomy.agent;

import com.googlecode.greysanatomy.console.command.Command.Info;
import com.googlecode.greysanatomy.probe.JobListener;
import com.googlecode.greysanatomy.probe.ProbeJobs;
import com.googlecode.greysanatomy.probe.Probes;
import com.googlecode.greysanatomy.util.GaReflectUtils;
import javassist.*;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.lang.instrument.Instrumentation;
import java.lang.instrument.UnmodifiableClassException;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.googlecode.greysanatomy.probe.ProbeJobs.register;
import static com.googlecode.greysanatomy.util.LogUtils.*;
import static java.lang.System.arraycopy;

public class GreysAnatomyClassFileTransformer implements ClassFileTransformer {

    //    private final String prefClzRegex;
    private final String prefMthRegex;
    private final int id;
    private final List<CtBehavior> modifiedBehaviors;

    /*
     * ��֮ǰ���������һ������
     */
    private final static Map<String, byte[]> classBytesCache = new ConcurrentHashMap<String, byte[]>();

    private GreysAnatomyClassFileTransformer(
//            final String prefClzRegex,
            final String prefMthRegex,
            final JobListener listener,
            final List<CtBehavior> modifiedBehaviors,
            final Info info) {
//        this.prefClzRegex = prefClzRegex;
        this.prefMthRegex = prefMthRegex;
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

//        // ����жϿ���ȥ���ˣ�������Ѿ����й�һ��
//        if (!className.matches(prefClzRegex)) {
//            return null;
//        }

        // ������һ���������ƣ���ֹ���߲���������б��룬Ӱ�컺��
        synchronized (classBytesCache) {
            final ClassPool cp = new ClassPool(null);

            // TODO : ��������loader.toString()��Ϊkey��ɣ��ܿ��ܻ����key��ͻ�Լ��ڴ�й©��Ӧ����WeakHashMap���и���
            final String cacheKey = className + "@" + loader;
            if (classBytesCache.containsKey(cacheKey)) {
                // ���ȼ�1:
                // �����ֽ��뻺�棬���޴ӻ������
                // �ֽ��뻺������Ҫ������������������jobͬʱ��Ⱦ��һ��Class��
                cp.appendClassPath(new ByteArrayClassPath(className, classBytesCache.get(cacheKey)));
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
                        if (cb.getMethodInfo().getName().matches(prefMthRegex)) {
                            modifiedBehaviors.add(cb);
                            Probes.mine(id, cc, cb);
                        }

                        //  ��������ƥ��������ʽ
                        else {
                            debug("class=%s;method=%s was not matches regex=%s", className, cb.getMethodInfo().getName(), prefMthRegex);
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

            classBytesCache.put(cacheKey, data);
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
                                            final String prefClzRegex,
                                            final String prefMthRegex,
                                            final JobListener listener,
                                            final Info info,
                                            final boolean isForEach) throws UnmodifiableClassException {
        return transform(instrumentation, prefClzRegex, prefMthRegex, listener, info, isForEach, null);
    }

    /**
     * ��������α�
     *
     * @param instrumentation instrumentation
     * @param prefClzRegex    ������������ʽ
     * @param prefMthRegex    ������������ʽ
     * @param listener        ���������
     * @return ��Ⱦ���
     * @throws UnmodifiableClassException
     */
    public static TransformResult transform(final Instrumentation instrumentation,
                                            final String prefClzRegex,
                                            final String prefMthRegex,
                                            final JobListener listener,
                                            final Info info,
                                            final boolean isForEach,
                                            final Progress progress) throws UnmodifiableClassException {

        final List<CtBehavior> modifiedBehaviors = new ArrayList<CtBehavior>();
        final GreysAnatomyClassFileTransformer transformer
                = new GreysAnatomyClassFileTransformer(prefMthRegex, listener, modifiedBehaviors, info);
        instrumentation.addTransformer(transformer, true);

        final Collection<Class<?>> modifiedClasses = classesRegexMatch(instrumentation, prefClzRegex);
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
     * �ҵ�����������ʽҪ�����
     *
     * @param instrumentation instrumentation
     * @param prefClzRegex    ������������ʽ
     * @return ����������ʽ���༯��
     */
    private static Collection<Class<?>> classesRegexMatch(final Instrumentation instrumentation,
                                                          final String prefClzRegex) {
        final List<Class<?>> modifiedClasses = new ArrayList<Class<?>>();
        for (Class<?> clazz : instrumentation.getAllLoadedClasses()) {
            if (clazz.getName().matches(prefClzRegex)) {
                modifiedClasses.add(clazz);
                debug("class:%s was matched;", clazz);
            }
        }//for

        info("ClzRegex was %s, found size[%s] classes was matched.", prefClzRegex, modifiedClasses.size());

        return modifiedClasses;
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
