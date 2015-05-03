package com.googlecode.greysanatomy.server;

import com.googlecode.greysanatomy.Configure;
import com.googlecode.greysanatomy.util.GaStringUtils;
import com.googlecode.greysanatomy.util.IOUtils;
import com.googlecode.greysanatomy.util.LogUtils;

import java.io.IOException;
import java.lang.instrument.Instrumentation;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.charset.Charset;
import java.util.Iterator;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

import static java.lang.String.format;
import static java.nio.channels.SelectionKey.OP_ACCEPT;
import static java.nio.channels.SelectionKey.OP_READ;

/**
 * �н���
 */
enum LineDecodeState {

    // ���ַ�
    READ_CHAR,

    // ������
    READ_EOL
}

/**
 * Greys �����<br/>
 * Created by vlinux on 15/5/2.
 */
public class GaServer {

    private final Logger logger = LogUtils.getLogger();

    private static final int BUFFER_SIZE = 4 * 1024;
    private static final Charset DEFAULT_CHARSET = Charset.forName("UTF-8");
    private static final byte CTRL_D = 0x04;

    private final AtomicBoolean isBindRef = new AtomicBoolean(false);
    private final GaSessionManager gaSessionManager;
    private final CommandHandler commandHandler;

    private final ExecutorService executorService = Executors.newCachedThreadPool(new ThreadFactory() {
        @Override
        public Thread newThread(Runnable r) {
            final Thread t = new Thread(r, "GaCommand-execute-daemon");
            t.setDaemon(true);
            return t;
        }
    });

    private GaServer(Instrumentation instrumentation) {
        this.gaSessionManager = new DefaultGaSessionManager();
        this.commandHandler = new DefaultCommandHandler(this, instrumentation);

        Runtime.getRuntime().addShutdownHook(new Thread("GaServer-ShutdownHook-Thread") {

            @Override
            public void run() {
                executorService.shutdown();
                commandHandler.destroy();
                gaSessionManager.destroy();
                if (isBind()) {
                    unbind();
                }
            }
        });

    }

    /**
     * �жϷ�����Ƿ��Ѿ�����
     *
     * @return true:������Ѿ�����;false:����˹ر�
     */
    public boolean isBind() {
        return isBindRef.get();
    }


    private ServerSocketChannel serverSocketChannel = null;
    private Selector selector = null;

    /**
     * ����Greys�����
     *
     * @param configure
     * @throws IOException
     */
    public void bind(Configure configure) throws IOException {
        if (!isBindRef.compareAndSet(false, true)) {
            throw new IllegalStateException("already bind");
        }

        try {

            serverSocketChannel = ServerSocketChannel.open();
            selector = Selector.open();

            serverSocketChannel.configureBlocking(false);
            serverSocketChannel.socket().setSoTimeout(configure.getConnectTimeout());
            serverSocketChannel.socket().setReuseAddress(true);
            serverSocketChannel.register(selector, OP_ACCEPT);

            // ���������ض˿�
            serverSocketChannel.bind(new InetSocketAddress(configure.getTargetIp(), configure.getTargetPort()), 24);
            if (logger.isLoggable(Level.INFO)) {
                logger.log(Level.INFO, format("GaServer listened on network=%s;port=%d;timeout=%d;",
                        configure.getTargetIp(),
                        configure.getTargetPort(),
                        configure.getConnectTimeout()));
            }

            activeDoSelectDaemon(selector, configure);

        } catch (IOException e) {
            unbind();
            throw e;
        }

    }

    private void activeDoSelectDaemon(final Selector selector, final Configure configure) {

        final ByteBuffer byteBuffer = ByteBuffer.allocate(BUFFER_SIZE);

        final Thread gaServerSelectorDaemon = new Thread("GaServer-Selector-Daemon") {
            @Override
            public void run() {

                while (!isInterrupted()
                        && isBind()) {

                    try {

                        while (selector.select() > 0) {
                            final Iterator<SelectionKey> it = selector.selectedKeys().iterator();
                            while (it.hasNext()) {
                                final SelectionKey key = it.next();
                                it.remove();

                                // do ssc accept
                                if (key.isValid() && key.isAcceptable()) {
                                    doAccept(key, selector, configure);
                                }

                                // do sc read
                                if (key.isValid() && key.isReadable()) {
                                    doRead(byteBuffer, key);
                                }

                            }
                        }

                    } catch (IOException e) {
                        if (logger.isLoggable(Level.WARNING)) {
                            logger.log(Level.WARNING, format("%s selector failed.",
                                    GaServer.this), e);
                        }
                    } catch (ClosedSelectorException e) {
                        //
                    }


                }

            }
        };
        gaServerSelectorDaemon.setDaemon(true);
        gaServerSelectorDaemon.start();
    }

    private void doAccept(SelectionKey key, Selector selector, Configure configure) throws IOException {
        final ServerSocketChannel serverSocketChannel = (ServerSocketChannel) key.channel();
        final SocketChannel socketChannel = serverSocketChannel.accept();
        socketChannel.configureBlocking(false);
        socketChannel.socket().setSoTimeout(configure.getConnectTimeout());
        socketChannel.socket().setTcpNoDelay(true);

        socketChannel.register(selector, OP_READ, new GaAttachment(
                BUFFER_SIZE,
                gaSessionManager.newGaSession(socketChannel, DEFAULT_CHARSET)));
        if (logger.isLoggable(Level.INFO)) {
            logger.log(Level.INFO, format("%s accept an connection, client=%s;",
                    GaServer.this,
                    socketChannel));
        }

        // �������Logo
        socketChannel.write(ByteBuffer.wrap(GaStringUtils.getLogo().getBytes(DEFAULT_CHARSET)));

        // ������ʾ��
        reDrawPrompt(socketChannel, DEFAULT_CHARSET);

    }

    private void doRead(final ByteBuffer byteBuffer, SelectionKey key) {
        final GaAttachment attachment = (GaAttachment) key.attachment();
        final SocketChannel socketChannel = (SocketChannel) key.channel();
        final GaSession gaSession = attachment.getGaSession();
        try {

            final int n = socketChannel.read(byteBuffer);

            // ������-1����˵��SocketChannel�Ѿ��ر�
            if (n == -1) {
                if (logger.isLoggable(Level.INFO)) {
                    logger.log(Level.INFO, format("client=%s was closed, for %s",
                            socketChannel,
                            GaServer.this));
                }
                closeSocketChannel(key, socketChannel);
            }

            // ���������ݴ���0��˵������������
            else {
                byteBuffer.flip();

                while (true) {
                    if (!byteBuffer.hasRemaining()) {
                        break;
                    }
                    switch (attachment.getLineDecodeState()) {
                        case READ_CHAR: {
                            final byte data = byteBuffer.get();
                            if ('\n' == data) {
                                attachment.setLineDecodeState(LineDecodeState.READ_EOL);
                            }

                            // ������ֹ����(CTRL_D)�����ǻỰΪ����д���ú�̨����ͣ��
                            else if (CTRL_D == data) {
                                gaSession.markJobRunning(false);

//                                // ������ֹ��ʱ�򲻻����κ�ˢ�£�������Ҫ���»�����ʾ��
//                                // �����ڲ����ն�ʵ�ֵ�ʱ��CTRL_D�������ڻس����ͣ������ڰ��µ�ͬʱ�ͷ��͹���
//                                // ����������Ҫ����ʾ��֮�������һ�����з�
//                                socketChannel.write(ByteBuffer.wrap("\n".getBytes(gaSession.getCharset())));
//                                reDrawPrompt(socketChannel, gaSession.getCharset());
                                break;
                            }

                            // ��ͨbyte��������뵽������
                            else {
                                if ('\r' != data) {
                                    attachment.put(data);
                                }
                                break;
                            }
                        }

                        case READ_EOL: {
                            final String line = attachment.clearAndGetLine(gaSession.getCharset());

                            // ֻ��û�������ں�̨���е�ʱ����ܽ��ܷ������Ӧ
                            if (!gaSession.hasJobRunning()) {

                                // ֻ����������Ч�ַ��Ž����������
                                if (GaStringUtils.isNotBlank(line)) {

                                    executorService.execute(new Runnable() {
                                        @Override
                                        public void run() {
                                            try {
                                                commandHandler.executeCommand(line, gaSession);
                                            } catch (IOException e) {
                                                final String message = String.format("command execute failed, sessionId=%d;", gaSession.getSessionId());
                                                if (logger.isLoggable(Level.WARNING)) {
                                                    logger.log(Level.WARNING, message);
                                                }
                                                if (logger.isLoggable(Level.INFO)) {
                                                    logger.log(Level.INFO, message, e);
                                                }
                                                gaSession.destroy();
                                            }
                                        }
                                    });


                                }

                                // ��������ػ���ʾ��
                                else {
                                    reDrawPrompt(socketChannel, gaSession.getCharset());
                                }

                            }

                            attachment.setLineDecodeState(LineDecodeState.READ_CHAR);
                            break;
                        }
                    }
                }//while for line decode

                byteBuffer.clear();

            }

        } catch (IOException e) {
            if (logger.isLoggable(Level.WARNING)) {
                logger.log(Level.WARNING, format("read/write data failed, client=%s will be close, for %s",
                        socketChannel,
                        GaServer.this));
            }
            if (logger.isLoggable(Level.FINE)) {
                logger.log(Level.FINE, format("read/write data failed, client=%s will be close, for %s",
                        socketChannel,
                        GaServer.this), e);
            }
            closeSocketChannel(key, socketChannel);
            gaSession.destroy();
        }
    }


    /*
     * ������ʾ��
     */
    private void reDrawPrompt(SocketChannel socketChannel, Charset charset) throws IOException {
        socketChannel.write(ByteBuffer.wrap(GaStringUtils.DEFAULT_PROMPT.getBytes(charset)));
    }

    private void closeSocketChannel(SelectionKey key, SocketChannel socketChannel) {
        IOUtils.close(socketChannel);
        key.cancel();
    }

    /**
     * �ر�Greys�����
     */
    public void unbind() {

        gaSessionManager.clean();

        IOUtils.close(serverSocketChannel);
        IOUtils.close(selector);

        if (!isBindRef.compareAndSet(true, false)) {
            throw new IllegalStateException("already unbind");
        }
    }

    private static volatile GaServer gaServer;

    /**
     * ����
     *
     * @param instrumentation JVM��ǿ
     * @return GaServer����
     */
    public static GaServer getInstance(final Instrumentation instrumentation) {
        if (null == gaServer) {
            synchronized (GaServer.class) {
                if (null == gaServer) {
                    gaServer = new GaServer(instrumentation);
                }
            }
        }
        return gaServer;
    }

}
