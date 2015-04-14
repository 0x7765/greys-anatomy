package com.googlecode.greysanatomy.util;

import static com.googlecode.greysanatomy.util.LogUtils.info;
import static com.googlecode.greysanatomy.util.LogUtils.warn;

/**
 * JVM������ع�����
 *
 * @author vlinux
 */
public class JvmUtils {


    /**
     * �رչ���
     *
     * @author vlinux
     */
    public static interface ShutdownHook {

        /**
         * ���Թر�
         *
         * @throws Throwable
         */
        void shutdown() throws Throwable;

    }

    /**
     * ��JVMע��һ���رյ�Hook
     *
     * @param name
     * @param shutdownHook
     */
    public static void registShutdownHook(final String name, final ShutdownHook shutdownHook) {

        info("reg shutdown hook %s.", name);
        Runtime.getRuntime().addShutdownHook(new Thread() {

            @Override
            public void run() {
                try {
                    shutdownHook.shutdown();
                    info("%s shutdown success.", name);
                } catch (Throwable t) {
                    warn(t, "%s shutdown failed, ignore it.", name);
                }
            }

        });

    }

}
