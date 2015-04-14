package com.googlecode.greysanatomy.console.client;

import com.googlecode.greysanatomy.Configure;
import com.googlecode.greysanatomy.console.GreysAnatomyConsole;
import com.googlecode.greysanatomy.console.rmi.req.ReqHeart;
import com.googlecode.greysanatomy.console.server.ConsoleServerService;
import com.googlecode.greysanatomy.exception.PIDNotMatchException;
import com.googlecode.greysanatomy.util.LogUtils;

import java.rmi.Naming;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * ����̨�ͻ���
 *
 * @author chengtongda
 */
public class ConsoleClient {

    private final Logger logger = LogUtils.getLogger();
    private final ConsoleServerService consoleServer;
    private final long sessionId;

    private ConsoleClient(Configure configure) throws Exception {
        this.consoleServer = (ConsoleServerService) Naming.lookup(String.format("rmi://%s:%d/RMI_GREYS_ANATOMY",
                configure.getTargetIp(),
                configure.getTargetPort()));

        // ���PID�Ƿ���ȷ
        if (!consoleServer.checkPID(configure.getJavaPid())) {
            throw new PIDNotMatchException();
        }

        this.sessionId = this.consoleServer.register();
        new GreysAnatomyConsole(configure, sessionId).start(consoleServer);
        heartBeat();
    }

    /**
     * ������������߳�
     */
    private void heartBeat() {
        Thread heartBeatDaemon = new Thread("ga-console-client-heartbeat") {

            @Override
            public void run() {
                while (true) {
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        //
                    }
                    if (null == consoleServer) {
                        // �����ѹرգ��ͻ�������Ҳûɶ��˼�ˣ��������˳�JVM
                        if (logger.isLoggable(Level.INFO)) {
                            logger.log(Level.INFO, "disconnect to ga-console-server, shutdown jvm.");
                        }
                        System.exit(0);
                        break;
                    } else {
                        boolean hearBeatResult = false;
                        try {
                            hearBeatResult = consoleServer.sessionHeartBeat(new ReqHeart(sessionId));
                        } catch (Exception e) {
                            //
                        }
                        //�������ʧ�ܣ���˵����ʱ�ˣ��Ǿ�gg��
                        if (!hearBeatResult) {
                            if (logger.isLoggable(Level.INFO)) {
                                logger.log(Level.INFO, "session time out to ga-console-server, shutdown jvm.");
                            }
                            System.exit(0);
                            break;
                        }
                    }
                }
            }

        };
        heartBeatDaemon.setDaemon(true);
        heartBeatDaemon.start();
    }

    private static volatile ConsoleClient instance;

    /**
     * ��ʼ����������̨�ͻ���
     *
     * @param configure �����ļ�
     * @throws Exception �������ƶ�ʧ��
     */
    public static synchronized ConsoleClient getInstance(Configure configure) throws Exception {
        if (null == instance) {
            instance = new ConsoleClient(configure);
        }
        return instance;
    }

}
