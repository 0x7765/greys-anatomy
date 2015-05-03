package com.googlecode.greysanatomy.server;

import java.io.IOException;

/**
 * �������
 * Created by vlinux on 15/5/3.
 */
public interface CommandHandler {

    /**
     * ���������в�ִ������
     *
     * @param line      �����������
     * @param gaSession �Ự
     * @throws IOException IO����
     */
    void executeCommand(final String line, final GaSession gaSession) throws IOException;


    /**
     * ����
     */
    void destroy();

}
