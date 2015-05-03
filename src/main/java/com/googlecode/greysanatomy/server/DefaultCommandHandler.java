package com.googlecode.greysanatomy.server;

import com.googlecode.greysanatomy.command.Command;
import com.googlecode.greysanatomy.command.Commands;
import com.googlecode.greysanatomy.command.QuitCommand;
import com.googlecode.greysanatomy.command.ShutdownCommand;
import com.googlecode.greysanatomy.exception.CommandInitializationException;
import com.googlecode.greysanatomy.exception.CommandNotFoundException;
import com.googlecode.greysanatomy.probe.ProbeJobs;
import com.googlecode.greysanatomy.util.GaStringUtils;
import com.googlecode.greysanatomy.util.LogUtils;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.instrument.Instrumentation;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

import static java.lang.String.format;

/**
 * �������
 * Created by vlinux on 15/5/2.
 */
public class DefaultCommandHandler implements CommandHandler {


    private final Logger logger = LogUtils.getLogger();

    private static final int BUFFER_SIZE = 4 * 1024;

    private final GaServer gaServer;
    private final Instrumentation instrumentation;
    private final ExecutorService executorService = Executors.newCachedThreadPool(new ThreadFactory() {
        @Override
        public Thread newThread(Runnable r) {
            final Thread t = new Thread(r, "GaCommand-execute-daemon");
            t.setDaemon(true);
            return t;
        }
    });

    public DefaultCommandHandler(GaServer gaServer, Instrumentation instrumentation) {
        this.gaServer = gaServer;
        this.instrumentation = instrumentation;
    }

    @Override
    public void executeCommand(final String line, final GaSession gaSession) throws IOException {

        final SocketChannel socketChannel = gaSession.getSocketChannel();

        try {
            execute(gaSession, socketChannel, Commands.getInstance().newCommand(line));
        }

        // �������
        catch (CommandNotFoundException e) {
            final String message = format("command \"%s\" not found.\n", e.getCommand());
            write(socketChannel, message, gaSession.getCharset());
            if (logger.isLoggable(Level.FINE)) {
                logger.log(Level.FINE, message, e);
            }
        }

        // �����ʼ��ʧ��
        catch (CommandInitializationException e) {
            final String message = format("command \"%s\"init failed.\n", e.getCommand());
            write(socketChannel, message, gaSession.getCharset());
            if (logger.isLoggable(Level.WARNING)) {
                logger.log(Level.WARNING, message, e);
            }
        }

        // �����ڲ�ִ�д���
        catch (Throwable t) {
            final String message = format("command execute failed : %s\n", GaStringUtils.getCauseMessage(t));
            write(socketChannel, message, gaSession.getCharset());
            if (logger.isLoggable(Level.INFO)) {
                logger.log(Level.INFO, message);
            }

            if (logger.isLoggable(Level.FINE)) {
                logger.log(Level.FINE, message, t);
            }
        }
    }


    /*
     * ִ������
     */
    private void execute(final GaSession gaSession, final SocketChannel socketChannel, final Command command) throws IOException {
        final AtomicBoolean isFinishRef = new AtomicBoolean(false);
        final int jobId = ProbeJobs.createJob();

        // ע�뵱ǰ�Ự��ִ�е�jobId�������ط���Ҫ
        gaSession.setCurrentJobId(jobId);

        final Command.Action action = command.getAction();
        final Command.Info info = new Command.Info(instrumentation, gaSession.getSessionId(), jobId);
        final Command.Sender sender = new Command.Sender() {

            @Override
            public void send(boolean isF, String message) {

                final Writer writer = ProbeJobs.getJobWriter(jobId);
                if (null != writer) {
                    try {
                        writer.write(message);

                        // ����Ϊ�����ۣ���ÿ������������һ�е�ʱ����
                        if (isF) {
                            writer.write("\n");
                            writer.flush();
                        }

                        writer.flush();
                    } catch (IOException e) {
                        if (logger.isLoggable(Level.WARNING)) {
                            logger.log(Level.WARNING, format("command write job failed. sessionId=%d;jobId=%d;",
                                    gaSession.getSessionId(),
                                    jobId), e);
                        }

                        // �������д�ļ�ʧ���ˣ���Ҫ������д����Ϊ���
                        // ������ִ���߳̾��������������д���ǳ����ɻ�����
                        // ���ڿ���û�����������
                        isFinishRef.set(true);
                    }
                }

                if (isF) {
                    isFinishRef.set(true);
                }

            }

        };

        executorService.execute(new Runnable() {
            @Override
            public void run() {

                final CharBuffer buffer = CharBuffer.allocate(BUFFER_SIZE);

                // �Ƚ��Ự��д��
                gaSession.markJobRunning(true);

                try {

                    final Thread currentThread = Thread.currentThread();
                    action.action(gaSession, info, sender);

                    while (!gaSession.isDestroy()
                            && gaSession.hasJobRunning()
                            && !currentThread.isInterrupted()) {

                        final Reader reader = ProbeJobs.getJobReader(jobId);
                        if (null != reader) {

                            // touch the session
                            gaSession.touch();

                            // ���Ƚ�һ�������ݶ�ȡ��buffer��
                            if (-1 == reader.read(buffer)) {

                                // ������EOF��ʱ��ͬʱSender���ΪisFinished
                                // ˵��������������ˣ���������ỰΪ����д����������ѭ��
                                if (isFinishRef.get()) {
                                    gaSession.markJobRunning(false);
                                    break;
                                }

                                // ���Ѿ����ļ�����EOF��˵����ȡ��д��죬��Ҫ��Ϣ��
                                // ���500ms����������޸�֪
                                try {
                                    Thread.sleep(500);
                                } catch (InterruptedException e) {
                                    currentThread.interrupt();
                                }

                            }

                            // �����˵㶫��
                            else {

                                buffer.flip();

                                final ByteBuffer writeByteBuffer = gaSession.getCharset().encode(buffer);

                                while (writeByteBuffer.hasRemaining()) {

                                    if (-1 == gaSession.getSocketChannel().write(writeByteBuffer)) {
                                        // socket broken
                                        if (logger.isLoggable(Level.INFO)) {
                                            logger.log(Level.INFO, format("JobId[%d] write failed, because socket broken, GaSession[%d] will be destroy.",
                                                    jobId,
                                                    gaSession.getSessionId()));
                                            gaSession.destroy();
                                        }
                                    }

                                }//while for write


                                buffer.clear();

                            }


                        }

                    }//while command running

                }

                // �����رյ����ӿ��Ժ���
                catch (ClosedChannelException e) {

                    final String message = format("write failed, because socket broken. sessionId=%d;jobId=%d;\n",
                            gaSession.getSessionId(),
                            jobId);
                    if (logger.isLoggable(Level.FINE)) {
                        logger.log(Level.FINE, message, e);
                    }

                }

                // ������������¼
                catch (Throwable t) {
                    final String message = format("command execute failed, %s\n",
                            GaStringUtils.getCauseMessage(t));
                    if (logger.isLoggable(Level.WARNING)) {
                        logger.log(Level.WARNING, message, t);
                    }

                    try {
                        write(socketChannel, message, gaSession.getCharset());
                    } catch (IOException e) {
                        if (logger.isLoggable(Level.WARNING)) {
                            logger.log(Level.WARNING, format("write failed, will be destroy. sessionId=%d;", gaSession.getSessionId()), e);
                        }
                        gaSession.destroy();
                    }
                }

                // ������һЩ����
                finally {

                    // ��������Ľ����Σ�����Ҫ�رյ��Ự��д
                    gaSession.markJobRunning(false);

                    // ɱ����̨JOB
                    final Integer jobId = gaSession.getCurrentJobId();
                    if (null != jobId) {
                        ProbeJobs.killJob(jobId);
                    }


                    // �˳������Ҫ�ر�Socket
                    if (command instanceof QuitCommand) {
                        gaSession.destroy();
                    }

                    // �ر������Ҫ�ر����������
                    else if (command instanceof ShutdownCommand) {
                        DefaultCommandHandler.this.gaServer.unbind();
                    }

                    // ����������Ҫ���»�����ʾ��
                    else {

                        try {
                            write(socketChannel, GaStringUtils.DEFAULT_PROMPT, gaSession.getCharset());
                        } catch (IOException e) {
                            if (logger.isLoggable(Level.WARNING)) {
                                logger.log(Level.WARNING, format("write failed, will be destroy. sessionId=%d;", gaSession.getSessionId()), e);
                            }
                            gaSession.destroy();
                        }


                    }

                }

            }
        });
    }

    private void write(SocketChannel socketChannel, String message, Charset charset) throws IOException {
        socketChannel.write(ByteBuffer.wrap((message).getBytes(charset)));
    }

    @Override
    public void destroy() {
        executorService.shutdown();
    }


}
