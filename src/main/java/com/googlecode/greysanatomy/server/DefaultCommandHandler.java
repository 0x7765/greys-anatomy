package com.googlecode.greysanatomy.server;

import com.googlecode.greysanatomy.command.Command;
import com.googlecode.greysanatomy.command.Commands;
import com.googlecode.greysanatomy.command.QuitCommand;
import com.googlecode.greysanatomy.command.ShutdownCommand;
import com.googlecode.greysanatomy.exception.CommandException;
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

    public DefaultCommandHandler(GaServer gaServer, Instrumentation instrumentation) {
        this.gaServer = gaServer;
        this.instrumentation = instrumentation;
    }

    @Override
    public void executeCommand(final String line, final GaSession gaSession) throws IOException {

        final SocketChannel socketChannel = gaSession.getSocketChannel();

        try {
            final Command command = Commands.getInstance().newCommand(line);
            execute(gaSession, socketChannel, command);

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
                write(socketChannel, GaStringUtils.DEFAULT_PROMPT, gaSession);
            }

        }

        // �������
        catch (CommandNotFoundException e) {
            final String message = format("command \"%s\" not found.\n", e.getCommand());
            write(socketChannel, message, gaSession);
            write(socketChannel, GaStringUtils.DEFAULT_PROMPT, gaSession);
            if (logger.isLoggable(Level.FINE)) {
                logger.log(Level.FINE, message, e);
            }
        }

        // �����ʼ��ʧ��
        catch (CommandInitializationException e) {
            final String message = format("command \"%s\" init failed.\n", e.getCommand());
            write(socketChannel, message, gaSession);
            write(socketChannel, GaStringUtils.DEFAULT_PROMPT, gaSession);
            if (logger.isLoggable(Level.WARNING)) {
                logger.log(Level.WARNING, message, e);
            }
        }

        // ����׼������(����У���)
        catch (CommandException t) {
            final String message = format("command \"%s\" execute failed : %s\n",
                    t.getCommand(), GaStringUtils.getCauseMessage(t));
            write(socketChannel, message, gaSession);
            write(socketChannel, GaStringUtils.DEFAULT_PROMPT, gaSession);
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


        final CharBuffer buffer = CharBuffer.allocate(BUFFER_SIZE);


        try {
            action.action(gaSession, info, sender);
        }

        // ����ִ�д�������¼
        catch (Throwable t) {
            final String message = format("command execute failed, %s\n",
                    GaStringUtils.getCauseMessage(t));
            if (logger.isLoggable(Level.WARNING)) {
                logger.log(Level.WARNING, message, t);
            }
            write(socketChannel, message, gaSession);

            return;
        }

        // ������
        jobRunning(gaSession, isFinishRef, jobId, buffer);


    }

    private void jobRunning(GaSession gaSession, AtomicBoolean isFinishRef, int jobId, CharBuffer buffer) throws IOException {
        // �Ƚ��Ự��д��
        gaSession.markJobRunning(true);

        try {

            final Thread currentThread = Thread.currentThread();
            try {

                while (!gaSession.isDestroy()
                        && gaSession.hasJobRunning()
                        && !currentThread.isInterrupted()) {

                    final Reader reader = ProbeJobs.getJobReader(jobId);
                    if( null == reader ) {
                        break;
                    }

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
                        // ���200ms����������޸�֪
                        try {
                            Thread.sleep(200);
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
                                    logger.log(Level.INFO, format("write failed, because socket broken, session will be destroy. sessionId=%d;jobId=%d;",
                                            gaSession.getSessionId(),
                                            jobId));
                                    gaSession.destroy();
                                }
                            }

                        }//while for write


                        buffer.clear();

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

        }

        // ������һЩ����
        finally {

            // ��������Ľ����Σ�����Ҫ�رյ��Ự��д
            gaSession.markJobRunning(false);

            // ɱ����̨JOB
            ProbeJobs.killJob(jobId);

        }
    }

    private void write(SocketChannel socketChannel, String message, GaSession gaSession) throws IOException {
        socketChannel.write(ByteBuffer.wrap((message).getBytes(gaSession.getCharset())));
    }

    @Override
    public void destroy() {
        //
    }


}
