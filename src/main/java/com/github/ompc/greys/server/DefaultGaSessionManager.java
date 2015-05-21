package com.github.ompc.greys.server;

import com.github.ompc.greys.util.LogUtil;

import java.io.IOException;
import java.nio.ByteBuffer;
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
 * 默认会话管理器实现
 * Created by vlinux on 15/5/2.
 */
public class DefaultGaSessionManager implements SessionManager {

    private final Logger logger = LogUtil.getLogger();

    // 5分钟
    private static final long DURATION_5_MINUTE = 5L * 60 * 1000;

    // 会话超时时间
    private static final long DEFAULT_SESSION_DURATION = DURATION_5_MINUTE;

    // 会话管理Map
    private final ConcurrentHashMap<Integer, Session> gaSessionMap = new ConcurrentHashMap<Integer, Session>();

    // 会话ID序列生成器
    private final AtomicInteger gaSessionIndexSequence = new AtomicInteger(0);

    private final AtomicBoolean isDestroyRef = new AtomicBoolean(false);

    public DefaultGaSessionManager() {
        activeGaSessionExpireDaemon();
    }

    @Override
    public Session getGaSession(int sessionId) {
        return gaSessionMap.get(sessionId);
    }

    @Override
    public Session newSession(int javaPid, SocketChannel socketChannel, Charset charset) {
        final int gaSessionId = gaSessionIndexSequence.getAndIncrement();
        final Session session = new Session(javaPid, gaSessionId, DEFAULT_SESSION_DURATION, socketChannel, charset) {
            @Override
            public void destroy() {
                super.destroy();
                gaSessionMap.remove(gaSessionId);
            }
        };

        final Session sessionInMap = gaSessionMap.putIfAbsent(gaSessionId, session);
        if (null != sessionInMap) {
            // 之前竟然存在，返回之前的
            return sessionInMap;
        }

        return session;
    }

    /**
     * 激活会话过期管理守护线程
     */
    private void activeGaSessionExpireDaemon() {
        final Thread gaSessionExpireDaemon = new Thread("ga-session-expire-daemon") {

            @Override
            public void run() {
                while (!isDestroyRef.get()
                        && !isInterrupted()) {

                    // 间隔500ms检测一次
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException e) {
                        interrupt();
                    }

                    for (final Map.Entry<Integer, Session> entry : gaSessionMap.entrySet()) {

                        final int gaSessionId = entry.getKey();
                        final Session session = entry.getValue();
                        if (null == session
                                || session.isExpired()) {
                            if (logger.isLoggable(Level.INFO)) {
                                logger.log(Level.INFO, format("session was expired, sessionId=%d;", gaSessionId));
                            }

                            if (null != session) {

                                try {
                                    // 会话超时，关闭之前输出超时信息
                                    session.getSocketChannel().write(ByteBuffer.wrap("session expired.\n".getBytes()));
                                } catch (IOException e) {
                                    final String message = format("write expired message failed. sessionId=%d;", session.getSessionId());
                                    if (logger.isLoggable(Level.FINE)) {
                                        logger.log(Level.FINE, message, e);
                                    }
                                    if (logger.isLoggable(Level.INFO)) {
                                        logger.log(Level.INFO, message);
                                    }
                                }

                                session.destroy();
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
        for (Session session : gaSessionMap.values()) {
            session.destroy();
        }
    }

    @Override
    public void destroy() {

        if (!isDestroyRef.compareAndSet(false, true)) {
            throw new IllegalStateException("already destroy");
        }

        clean();

    }
}
