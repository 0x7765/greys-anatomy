package com.googlecode.greysanatomy.server;

import com.googlecode.greysanatomy.command.Command;
import com.googlecode.greysanatomy.command.Commands;
import com.googlecode.greysanatomy.command.QuitCommand;
import com.googlecode.greysanatomy.command.ShutdownCommand;
import com.googlecode.greysanatomy.exception.CommandException;
import com.googlecode.greysanatomy.exception.CommandInitializationException;
import com.googlecode.greysanatomy.exception.CommandNotFoundException;
import com.googlecode.greysanatomy.exception.GaExecuteException;
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

        // ֻ��û�������ں�̨���е�ʱ����ܽ��ܷ������Ӧ
        if (gaSession.hasJobRunning()) {
            if (logger.isLoggable(Level.FINE)) {
                logger.log(Level.FINE, format("session[%d] has running job[%s] ignore this command.",
                        gaSession.getSessionId(),
                        gaSession.getCurrentJobId()));
            }
            return;
        }

        // ֻ����������Ч�ַ��Ž����������
        // ��������ػ���ʾ��
        if (GaStringUtils.isBlank(line)) {
            reDrawPrompt(socketChannel, gaSession.getCharset());
            return;
        }

        try {
            final Command command = Commands.getInstance().newCommand(line);
            execute(gaSession, command);

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
                reDrawPrompt(socketChannel, gaSession.getCharset());
            }

        }

        // ����׼������(����У���)
        catch (CommandException t) {

            final String message;
            if (t instanceof CommandNotFoundException) {
                message = format("command \"%s\" not found.\n",
                        t.getCommand());
            } else if (t instanceof CommandInitializationException) {
                message = format("command \"%s\" init failed.\n",
                        t.getCommand());
            } else {
                message = format("command \"%s\" prepare failed : %s\n",
                        t.getCommand(), GaStringUtils.getCauseMessage(t));
            }

            write(socketChannel, message, gaSession.getCharset());
            reDrawPrompt(socketChannel, gaSession.getCharset());
            if (logger.isLoggable(Level.INFO)) {
                logger.log(Level.INFO, message);
            }

            if (logger.isLoggable(Level.FINE)) {
                logger.log(Level.FINE, message, t);
            }
        }

        // ����ִ�д���
        catch (GaExecuteException e) {
            final String message = format("command execute failed, %s\n",
                    GaStringUtils.getCauseMessage(e));
            if (logger.isLoggable(Level.WARNING)) {
                logger.log(Level.WARNING, message, e);
            }
            write(socketChannel, message, gaSession.getCharset());
        }

    }


    /*
     * ִ������
     */
    private void execute(final GaSession gaSession, final Command command) throws GaExecuteException, IOException {
        final AtomicBoolean isFinishRef = new AtomicBoolean(false);

        final int jobId;
        try {
            jobId = ProbeJobs.createJob();
            // ע�뵱ǰ�Ự��ִ�е�jobId�������ط���Ҫ
            gaSession.setCurrentJobId(jobId);
        } catch (IOException e) {
            throw new GaExecuteException(format("crate job failed. sessionId=%s", gaSession.getSessionId()), e);
        }

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
                                    gaSession.getSessionId(), jobId), e);
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
            throw new GaExecuteException(format("execute failed. sessionId=%s", gaSession.getSessionId()), t);
        }

        // ������
        jobRunning(gaSession, isFinishRef, jobId, buffer);


    }

    private void jobRunning(GaSession gaSession, AtomicBoolean isFinishRef, int jobId, CharBuffer buffer) throws IOException, GaExecuteException {
        // �Ƚ��Ự��д��
        gaSession.markJobRunning(true);

        try {

            final Thread currentThread = Thread.currentThread();
            try {

                while (!gaSession.isDestroy()
                        && gaSession.hasJobRunning()
                        && !currentThread.isInterrupted()) {

                    final Reader reader = ProbeJobs.getJobReader(jobId);
                    if (null == reader) {
                        break;
                    }

                    // touch the session
                    gaSession.touch();

                    final int readCount;
                    try {
                        readCount = reader.read(buffer);
                    } catch (IOException e) {
                        throw new GaExecuteException("read job message failed.", e);
                    }


                    // ���Ƚ�һ�������ݶ�ȡ��buffer��
                    if (-1 == readCount) {

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
                                    logger.log(Level.INFO, format("network communicate failed, session will be destroy. sessionId=%d;jobId=%d;",
                                            gaSession.getSessionId(), jobId));
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


    /*
     * ������ʾ��
     */
    private void reDrawPrompt(SocketChannel socketChannel, Charset charset) throws IOException {
        write(socketChannel, GaStringUtils.DEFAULT_PROMPT, charset);
    }

    private void write(SocketChannel socketChannel, String message, Charset charset) throws IOException {
        socketChannel.write(ByteBuffer.wrap((message).getBytes(charset)));
    }

    @Override
    public void destroy() {
        //
    }


}
