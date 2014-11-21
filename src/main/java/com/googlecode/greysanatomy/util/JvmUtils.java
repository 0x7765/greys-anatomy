package com.googlecode.greysanatomy.util;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * JVM������ع�����
 *
 * @author vlinux
 */
public class JvmUtils {

    private static final Logger logger = Logger.getLogger("greysanatomy");

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

        if(logger.isLoggable(Level.INFO)){
            logger.info(String.format("regist shutdown hook %s", name));
        }
        Runtime.getRuntime().addShutdownHook(new Thread() {

            @Override
            public void run() {
                try {
                    shutdownHook.shutdown();
                    if(logger.isLoggable(Level.INFO)){
                        logger.info(String.format("%s shutdown successed.", name));
                    }
                } catch (Throwable t) {
                    if(logger.isLoggable(Level.WARNING)){
                        logger.log(Level.WARNING,String.format("%s shutdown failed, ignore it.", name),t);
                    }
                }
            }

        });

    }

}
