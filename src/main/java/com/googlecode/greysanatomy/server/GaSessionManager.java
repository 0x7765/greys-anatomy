package com.googlecode.greysanatomy.server;

import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;

/**
 * Greys �Ự����
 * Created by vlinux on 15/5/2.
 */
public interface GaSessionManager {

    /**
     * ����һ���Ự
     *
     * @param javaPid       Java���̺�
     * @param socketChannel �Ự����Ӧ��SocketChannel
     * @param charset       �Ự�����ַ���
     * @return �����ĻỰ
     */
    GaSession newGaSession(int javaPid, SocketChannel socketChannel, Charset charset);

    /**
     * ��ȡһ���Ự
     *
     * @param gaSessionId �ỰID
     * @return ���ػỰ
     */
    GaSession getGaSession(int gaSessionId);

    /**
     * �ر����лỰ
     */
    void clean();

    /**
     * ���ٻỰ����������������лỰ
     */
    void destroy();

}
