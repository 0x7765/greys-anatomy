package com.googlecode.greysanatomy.agent;

import com.googlecode.greysanatomy.command.Command.Info;
import com.googlecode.greysanatomy.probe.JobListener;
import com.googlecode.greysanatomy.probe.ProbeJobs;
import com.googlecode.greysanatomy.probe.Probes;
import com.googlecode.greysanatomy.util.GaReflectUtils;
import com.googlecode.greysanatomy.util.LogUtils;
import com.googlecode.greysanatomy.util.PatternMatchingUtils;
import javassist.*;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.lang.instrument.Instrumentation;
import java.lang.instrument.UnmodifiableClassException;
import java.security.ProtectionDomain;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.googlecode.greysanatomy.probe.ProbeJobs.register;
import static com.googlecode.greysanatomy.util.SearchUtils.searchClassByClassPatternMatching;
import static com.googlecode.greysanatomy.util.SearchUtils.searchClassBySupers;
import static java.lang.System.arraycopy;

public class GreysAnatomyClassFileTransformer implements ClassFileTransformer {

    private static final Logger logger = LogUtils.getLogger();
    private final String prefMthPattern;
    private final boolean isRegEx;

    private final int id;
    private final List<CtBehavior> modifiedBehaviors;

    /*
     * ��֮ǰ���������һ������
     */
    private final static Map<Class<?>, byte[]> classBytesCache = new WeakHashMap<Class<?>, byte[]>();

    private GreysAnatomyClassFileTransformer(
            final String prefMthPattern,
            final boolean isRegEx,
            final JobListener listener,
            final List<CtBehavior> modifiedBehaviors,
            final Info info) {
        this.prefMthPattern = prefMthPattern;
        this.isRegEx = isRegEx;
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
                // �����ֽ��뻺�棬���ȴӻ������
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
                        if (PatternMatchingUtils.matching(cb.getMethodInfo().getName(), prefMthPattern, isRegEx)) {

                            if (logger.isLoggable(Level.FINE)) {
                                logger.log(Level.FINE, String.format(
                                                "class=%s;method=%s was matches pattern=%s",
                                                className,
                                                cb.getMethodInfo().getName(),
                                                prefMthPattern)
                                );
                            }

                            modifiedBehaviors.add(cb);
                            Probes.mine(id, cc, cb);
                        }

                        //  ��������ƥ��������ʽ
                        else {
                            if (logger.isLoggable(Level.FINE)) {
                                logger.log(Level.FINE, String.format(
                                                "class=%s;method=%s was not matches pattern=%s",
                                                className,
                                                cb.getMethodInfo().getName(),
                                                prefMthPattern)
                                );
                            }
                        }
                    }
                }

                data = cc.toBytecode();
            } catch (Exception e) {
                if (logger.isLoggable(Level.FINE)) {
                    logger.log(Level.FINE, String.format(
                                    "transform class failed. class=%s, ClassLoader=%s",
                                    className, loader)
                            , e
                    );
                }
                if (logger.isLoggable(Level.INFO)) {
                    logger.log(Level.INFO, String.format("transform class failed. class=%s, ClassLoader=%s", className, loader));
                }
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
    public interface Progress {

        void progress(int index, int total);

    }

    public static TransformResult transform(final Instrumentation instrumentation,
                                            final String prefClzPattern,
                                            final String prefMthPattern,
                                            final boolean isSuper,
                                            final boolean isRegEx,
                                            final JobListener listener,
                                            final Info info,
                                            final boolean isForEach) throws UnmodifiableClassException {
        return transform(instrumentation, prefClzPattern, prefMthPattern, isSuper, isRegEx, listener, info, isForEach, null);
    }

    /**
     * ��������α�
     *
     * @param instrumentation instrumentation
     * @param prefClzPattern  ������������ʽ
     * @param prefMthPattern  ������������ʽ
     * @param listener        ���������
     * @return ��Ⱦ���
     * @throws UnmodifiableClassException
     */
    public static TransformResult transform(final Instrumentation instrumentation,
                                            final String prefClzPattern,
                                            final String prefMthPattern,
                                            final boolean isSuper,
                                            final boolean isRegEx,
                                            final JobListener listener,
                                            final Info info,
                                            final boolean isForEach,
                                            final Progress progress) throws UnmodifiableClassException {

        final List<CtBehavior> modifiedBehaviors = new ArrayList<CtBehavior>();
        final GreysAnatomyClassFileTransformer transformer
                = new GreysAnatomyClassFileTransformer(prefMthPattern, isRegEx, listener, modifiedBehaviors, info);
        instrumentation.addTransformer(transformer, true);

        final Collection<Class<?>> modifiedClasses =

                isSuper
                        ? searchClassBySupers(instrumentation, searchClassByClassPatternMatching(instrumentation, prefClzPattern, isRegEx))
                        : searchClassByClassPatternMatching(instrumentation, prefClzPattern, isRegEx);


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
            if (logger.isLoggable(Level.INFO)) {
                logger.log(Level.INFO, String.format("job[id=%s] was killed, stop this reTransform.", info.getJobId()));
            }
            return;
        }

        final int size = modifiedClasses.size();
        final Class<?>[] classArray = new Class<?>[size];
        final Object[] objectArray = modifiedClasses.toArray();
        arraycopy(objectArray, 0, classArray, 0, size);

        try {
            instrumentation.retransformClasses(classArray);
            if (logger.isLoggable(Level.INFO)) {
                logger.log(Level.INFO, String.format("reTransformClasses:size=[%s]", size));
            }
            if (logger.isLoggable(Level.FINE)) {
                logger.log(Level.FINE, String.format("reTransformClasses:%s", modifiedClasses));
            }
        } catch (Throwable t) {
            if (logger.isLoggable(Level.FINE)) {
                logger.log(Level.FINE, String.format("reTransformClasses failed, classes=%s.", modifiedClasses), t);
            }
            if (logger.isLoggable(Level.WARNING)) {
                logger.log(Level.WARNING, String.format("reTransformClasses failed, size=%s.", size));
            }
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
                if (logger.isLoggable(Level.INFO)) {
                    logger.log(Level.INFO, String.format("job[id=%s] was killed, stop this reTransform.", info.getJobId()));
                }
                break;
            }
            try {
                instrumentation.retransformClasses(clazz);
                if (logger.isLoggable(Level.FINE)) {
                    logger.log(Level.FINE, String.format("reTransformClasses, index=%s;total=%s;class=%s;", index, total, clazz));
                }
            } catch (Throwable t) {
                if (logger.isLoggable(Level.FINE)) {
                    logger.log(Level.FINE, String.format("reTransformClasses failed, index=%s;total=%s;class=%s;", index, total, clazz), t);
                }
                if (logger.isLoggable(Level.WARNING)) {
                    logger.log(Level.WARNING, String.format("reTransformClasses failed, index=%s;total=%s;class=%s;", index, total, clazz), t);
                }
            } finally {
                if (null != progress) {
                    progress.progress(++index, total);
                }
            }//try
        }//for

    }

}
