package com.googlecode.greysanatomy.console.server;

import com.googlecode.greysanatomy.console.command.Command;
import com.googlecode.greysanatomy.console.command.Command.Action;
import com.googlecode.greysanatomy.console.command.Command.Info;
import com.googlecode.greysanatomy.console.command.Command.Sender;
import com.googlecode.greysanatomy.console.command.Commands;
import com.googlecode.greysanatomy.console.rmi.RespResult;
import com.googlecode.greysanatomy.console.rmi.req.ReqCmd;
import com.googlecode.greysanatomy.console.rmi.req.ReqGetResult;
import com.googlecode.greysanatomy.console.rmi.req.ReqHeart;
import com.googlecode.greysanatomy.console.rmi.req.ReqKillJob;
import com.googlecode.greysanatomy.exception.SessionTimeOutException;
import com.googlecode.greysanatomy.probe.ProbeJobs;
import com.googlecode.greysanatomy.util.JvmUtils;
import com.googlecode.greysanatomy.util.JvmUtils.ShutdownHook;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.instrument.Instrumentation;
import java.nio.CharBuffer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.googlecode.greysanatomy.console.server.SessionJobsHolder.*;
import static com.googlecode.greysanatomy.probe.ProbeJobs.createJob;

/**
 * ����̨����˴�����
 *
 * @author vlinux
 */
public class ConsoleServerHandler {

    private static final Logger logger = Logger.getLogger("greysanatomy");

    private final ConsoleServer consoleServer;
    private final Instrumentation inst;
    private final ExecutorService workers;

    public ConsoleServerHandler(ConsoleServer consoleServer, Instrumentation inst) {
        this.consoleServer = consoleServer;
        this.inst = inst;
        this.workers = Executors.newCachedThreadPool(new ThreadFactory() {

            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "ga-console-server-workers");
                t.setDaemon(true);
                return t;
            }

        });

        JvmUtils.registShutdownHook("ga-console-server", new ShutdownHook() {

            @Override
            public void shutdown() throws Throwable {
                if (null != workers) {
                    workers.shutdown();
                }
            }

        });

    }

    public RespResult postCmd(final ReqCmd cmd) throws IOException {
        final RespResult respResult = new RespResult();
        respResult.setSessionId(cmd.getGaSessionId());
        respResult.setJobId(createJob());


        // bugfix reg job when an job was created.
        try {
            SessionJobsHolder.registJob(cmd.getGaSessionId(), respResult.getJobId());
        } catch (SessionTimeOutException e) {
            throw new IOException(String.format("session[sessionId=%s] was timeout.",cmd.getGaSessionId()), e);
        }

        workers.execute(new Runnable() {

            @Override
            public void run() {
                // ��ͨ��������
                final Info info = new Info(inst, respResult.getSessionId(), respResult.getJobId());
                final Sender sender = new Sender() {

                    @Override
                    public void send(boolean isF, String message) {
                        write(respResult.getJobId(), isF, message);
                    }
                };

                try {
                    final Command command = Commands.getInstance().newRiscCommand(cmd.getCommand());
                    // �������
                    if (null == command) {
                        write(respResult.getJobId(), true, "command not found!");
                        return;
                    }
                    final Action action = command.getAction();
                    action.action(consoleServer, info, sender);
                } catch (Throwable t) {
                    // ִ������ʧ��
                    if(logger.isLoggable(Level.WARNING)){
                        logger.log(Level.WARNING,"do action failed.", t);
                    }
                    write(respResult.getJobId(), true, "do action failed. cause : " + t.getMessage());
                    return;
                }
            }

        });
        return respResult;
    }

    public long register() {
        return registSession();
    }

    public RespResult getCmdExecuteResult(ReqGetResult req) {
        RespResult respResult = new RespResult();
        if (!heartBeatSession(req.getGaSessionId())) {
            respResult.setMessage("session Timeout.please reload!");
            respResult.setFinish(true);
            return respResult;
        }
        read(req.getJobId(), req.getPos(), respResult);
        respResult.setFinish(isFinish(respResult.getMessage()));
//        logger.info("debug for req={},respResult.message={}",req,respResult.getMessage());

        // bugfix, when got an finish flag, kill this job
        if( respResult.isFinish() ) {
            SessionJobsHolder.unRegistJob(req.getGaSessionId(), req.getJobId());
        }

        return respResult;
    }

    /**
     * �ɵ�һ��Job
     *
     * @param req
     */
    public void killJob(ReqKillJob req) {
        unRegistJob(req.getGaSessionId(), req.getJobId());
    }

    /**
     * �Ự����
     *
     * @param req
     * @return
     */
    public boolean sessionHeartBeat(ReqHeart req) {
        return heartBeatSession(req.getGaSessionId());
    }

    private final String END_MASK = "" + (char) 29;                        //���ڱ���ļ������ı�ʶ��

    /**
     * д���
     *
     * @param jobId
     * @param isF
     * @param message
     */
    private void write(int jobId, boolean isF, String message) {
        //TODO �����ö����������棬����д�ļ����ܣ�������ܻ�Ӱ�챻probe�����Ч��
        if (isF) {
            message += END_MASK;
        }

        if (null == message || message.length() == 0) {
            return;
        }

        final Writer writer = ProbeJobs.getJobWriter(jobId);
        if (null != writer) {
            try {
                writer.append(message);
                writer.flush();
            } catch (IOException e) {
                if(logger.isLoggable(Level.WARNING)){
                    logger.log(Level.WARNING, String.format("write job message failed, jobId=%s.", jobId), e);
                }
            }
        }


    }

    /**
     * ��job�Ľ��
     *
     * @param jobId
     * @param pos
     * @param respResult
     */
    private void read(int jobId, int pos, RespResult respResult) {

        final CharBuffer buffer = CharBuffer.allocate(4028);
        final Reader reader = ProbeJobs.getJobReader(jobId);
        if (null != reader) {
            try {
                final int newPos = pos + reader.read(buffer);
                buffer.flip();
                respResult.setPos(newPos);
                respResult.setMessage(buffer.toString());
            } catch (IOException e) {
                if(logger.isLoggable(Level.WARNING)){
                    logger.log(Level.WARNING, String.format("read job failed, jobId=%s.", jobId), e);
                }
            }
        }

    }

    private boolean isFinish(String message) {

        if( null != message
                && message.length() > 0) {
            return message.endsWith(END_MASK);
        }

        return false;

    }
}
