package com.googlecode.greysanatomy.console.server;

import com.googlecode.greysanatomy.console.rmi.RespResult;
import com.googlecode.greysanatomy.console.rmi.req.ReqCmd;
import com.googlecode.greysanatomy.console.rmi.req.ReqGetResult;
import com.googlecode.greysanatomy.console.rmi.req.ReqHeart;
import com.googlecode.greysanatomy.console.rmi.req.ReqKillJob;

import java.rmi.Remote;

/**
 * ����̨�����interface
 *
 * @author chengtongda
 */
public interface ConsoleServerService extends Remote {

    /**
     * ��������
     *
     * @param cmd
     * @return
     */
    RespResult postCmd(ReqCmd cmd) throws Exception;

    /**
     * ע�����
     *
     * @return
     */
    long register() throws Exception;

    /**
     * �˶�PID�Ƿ���ȷ
     *
     * @param pid
     * @return
     * @throws Exception
     */
    boolean checkPID(int pid) throws Exception;

    /**
     * ��ȡ����ִ�н��
     *
     * @param req
     * @return
     */
    RespResult getCmdExecuteResult(ReqGetResult req) throws Exception;

    /**
     * ɱ������
     *
     * @param req
     */
    void killJob(ReqKillJob req) throws Exception;

    /**
     * session����
     *
     * @param req
     */
    boolean sessionHeartBeat(ReqHeart req) throws Exception;

}
