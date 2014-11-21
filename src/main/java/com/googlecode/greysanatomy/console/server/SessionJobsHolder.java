package com.googlecode.greysanatomy.console.server;

import com.googlecode.greysanatomy.console.rmi.req.GaSession;
import com.googlecode.greysanatomy.exception.SessionTimeOutException;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.googlecode.greysanatomy.probe.ProbeJobs.killJob;

/**
 * ����˵�ǰ������session��job������Ϣ
 *
 * @author chengtongda
 */
public class SessionJobsHolder {

    private static final Logger logger = Logger.getLogger("greysanatomy");

    // �Ự��Ϣ
    private final static Map<Long, GaSession> sessionHolder = new ConcurrentHashMap<Long, GaSession>();

    static {
        //session������
        Thread sessionHearCheck = new Thread("ga-console-server-heartCheck") {

            private final long tip = 3000;

            @Override
            public void run() {
                while (true) {
                    try {
                        Thread.sleep(tip);
                    } catch (InterruptedException e) {
                        //
                    }
                    long currentMills = System.currentTimeMillis();
                    Set<Long> deadSessionIds = new HashSet<Long>();
                    for (GaSession session : sessionHolder.values()) {
                        //����Ѿ�����sessionʧЧ��ֵ����kill��session
                        if (currentMills - session.getLastModified() > tip) {
                            deadSessionIds.add(session.getSessionId());
                        }
                    }
                    //��ʧЧ��sessionȫ��kill��
                    for (Long deadSessionId : deadSessionIds) {
                        unRegistSession(deadSessionId);
                    }
                }
            }

        };
        sessionHearCheck.setDaemon(true);
        sessionHearCheck.start();
    }

    /**
     * ע��һ���Ự
     */
    public static synchronized long registSession() {
        GaSession session = new GaSession();
        sessionHolder.put(session.getSessionId(), session);
        if(logger.isLoggable(Level.INFO)){
            logger.info(String.format("regist session=%s", session.getSessionId()));
        }

        return session.getSessionId();
    }

    /**
     * session����
     *
     * @param gaSessionId
     * @return falseΪsession��ʧЧ
     */
    public static synchronized boolean heartBeatSession(long gaSessionId) {
        GaSession holderSession = sessionHolder.get(gaSessionId);
        //ע����������Ҫ�жϻỰ�Ƿ��ڣ���ʹ����Ҳ����ע��
        if (holderSession == null || !holderSession.isAlive()) {
            return false;
        }
        holderSession.setLastModified(System.currentTimeMillis());
        return true;
    }

    /**
     * ע��һ������
     *
     * @param gaSessionId
     * @param jobId
     */
    public static synchronized void unRegistJob(long gaSessionId, int jobId) {
        GaSession holderSession = sessionHolder.get(gaSessionId);
        //ע����������Ҫ�жϻỰ�Ƿ��ڣ���ʹ����Ҳ����ע��
        if (holderSession != null) {
            final Iterator<Integer> it = holderSession.getJobIds().iterator();
            while (it.hasNext()) {
                final int id = it.next();
//                if (StringUtils.equals(id, jobId)) {
                if( id == jobId ) {
                    killJob(id);
                    it.remove();
                    if(logger.isLoggable(Level.INFO)){
                        logger.info(String.format("unRegist job=%s for session=%s", id, gaSessionId));
                    }
                }
            }
        }
    }

    /**
     * ע��һ��job
     *
     * @param sessionId
     * @param jobId
     * @throws SessionTimeOutException
     */
    public static synchronized void registJob(long sessionId, int jobId) throws SessionTimeOutException {
        GaSession holderSession = sessionHolder.get(sessionId);
        if (holderSession == null || !holderSession.isAlive()) {
            throw new SessionTimeOutException("session is not exsit!");
        }
        holderSession.getJobIds().add(jobId);
        if(logger.isLoggable(Level.INFO)){
            logger.info(String.format("regist job=%s for session=%s", jobId, sessionId));
        }

    }

    /**
     * ע��һ���Ự
     *
     * @param gaSessionId
     */
    public static synchronized void unRegistSession(long gaSessionId) {
        GaSession holderSession = sessionHolder.get(gaSessionId);
        if (holderSession == null) {
            return;
        }
        holderSession.setAlive(false);
        final Iterator<Integer> it = holderSession.getJobIds().iterator();
        while (it.hasNext()) {
            killJob(it.next());
        }
        sessionHolder.remove(gaSessionId);
        if(logger.isLoggable(Level.INFO)){
            logger.info(String.format("unRegist session=%s", gaSessionId));
        }

    }
}
