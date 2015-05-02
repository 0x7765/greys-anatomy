package com.googlecode.greysanatomy.server;

import com.googlecode.greysanatomy.util.LogUtils;

import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

import static java.lang.String.format;

/**
 * Ĭ�ϻỰ������ʵ��
 * Created by vlinux on 15/5/2.
 */
public class DefaultGaSessionManager implements GaSessionManager {

    private final Logger logger = LogUtils.getLogger();

    // 5����
    private static final long DURATION_5_MINUTE = 5L * 60 * 1000;

    // �Ự��ʱʱ��
    private static final long DEFAULT_SESSION_DURATION = DURATION_5_MINUTE;

    // �Ự����Map
    private final ConcurrentHashMap<Integer, GaSession> gaSessionMap = new ConcurrentHashMap<Integer, GaSession>();

    // �ỰID����������
    private final AtomicInteger gaSessionIndexSequence = new AtomicInteger(0);

    private final AtomicBoolean isDestroyRef = new AtomicBoolean(false);

    public DefaultGaSessionManager() {
        activeGaSessionExpireDaemon();
    }

    @Override
    public GaSession getGaSession(int gaSessionId) {
        return gaSessionMap.get(gaSessionId);
    }

    @Override
    public GaSession newGaSession(SocketChannel socketChannel, Charset charset) {
        final int gaSessionId = gaSessionIndexSequence.getAndIncrement();
        final GaSession gaSession = new GaSession(gaSessionId, DEFAULT_SESSION_DURATION, socketChannel, charset) {
            @Override
            public void destroy() {
                super.destroy();
                gaSessionMap.remove(gaSessionId);
            }
        };

        final GaSession gaSessionInMap = gaSessionMap.putIfAbsent(gaSessionId, gaSession);
        if (null != gaSessionInMap) {
            // ֮ǰ��Ȼ���ڣ�����֮ǰ��
            return gaSessionInMap;
        }

        return gaSession;
    }

    /**
     * ����Ự���ڹ����ػ��߳�
     */
    private void activeGaSessionExpireDaemon() {
        final Thread gaSessionExpireDaemon = new Thread("GaSession-Expire-Daemon"){

            @Override
            public void run() {
                while (!isDestroyRef.get()
                        && !isInterrupted()) {

                    // ���500ms���һ��
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException e) {
                        interrupt();
                    }

                    for (final Map.Entry<Integer, GaSession> entry : gaSessionMap.entrySet()) {

                        final int gaSessionId = entry.getKey();
                        final GaSession gaSession = entry.getValue();
                        if (null == gaSession
                                || gaSession.isExpired()) {
                            if (logger.isLoggable(Level.INFO)) {
                                logger.log(Level.INFO, format("GaSession[%d] was expired.", gaSessionId));
                            }

                            if (null != gaSession) {
                                gaSession.destroy();
                            }

                            gaSessionMap.remove(gaSessionId);

                        }

                    }

                }
            }
        };
        gaSessionExpireDaemon.setDaemon(true);
        gaSessionExpireDaemon.start();
    }


    @Override
    public void clean() {
        // shutdown all the gaSession
        for (GaSession gaSession : gaSessionMap.values()) {
            gaSession.destroy();
        }
    }

    @Override
    public void destroy() {

        if (!isDestroyRef.compareAndSet(false, true)) {
            throw new IllegalStateException("GaSessionManager was already destroy");
        }

        clean();

    }
}
