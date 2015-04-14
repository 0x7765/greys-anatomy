package com.googlecode.greysanatomy.console.server;

import com.googlecode.greysanatomy.Configure;
import com.googlecode.greysanatomy.console.rmi.RespResult;
import com.googlecode.greysanatomy.console.rmi.req.ReqCmd;
import com.googlecode.greysanatomy.console.rmi.req.ReqGetResult;
import com.googlecode.greysanatomy.console.rmi.req.ReqHeart;
import com.googlecode.greysanatomy.console.rmi.req.ReqKillJob;
import com.googlecode.greysanatomy.util.GaStringUtils;
import com.googlecode.greysanatomy.util.HostUtils;

import java.lang.instrument.Instrumentation;
import java.net.MalformedURLException;
import java.rmi.*;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;

import static com.googlecode.greysanatomy.util.LogUtils.debug;
import static com.googlecode.greysanatomy.util.LogUtils.info;

/**
 * ����̨������
 *
 * @author vlinux
 */
public class ConsoleServer extends UnicastRemoteObject implements ConsoleServerService {

    private static final long serialVersionUID = 7625219488001802803L;


    private final ConsoleServerHandler serverHandler;
    private final Configure configure;

    private Registry registry;
    private boolean bind = false;

    static {

        // fix java.rmi.server.hostname, if it was invalid
        final String javaRmiServerHostname = System.getProperty("java.rmi.server.hostname");
        if (GaStringUtils.isNotBlank(javaRmiServerHostname)
                && !HostUtils.getAllLocalHostIP().contains(javaRmiServerHostname)) {
            System.setProperty("java.rmi.server.hostname", "127.0.0.1");
        }

    }

    private ConsoleServer(Configure configure, final Instrumentation inst) throws RemoteException, MalformedURLException, AlreadyBoundException {
        super();
        serverHandler = new ConsoleServerHandler(this, inst);
        this.configure = configure;
        rebind();
    }

    /**
     * ��Naming
     *
     * @throws MalformedURLException
     * @throws RemoteException
     * @throws AlreadyBoundException
     */
    public synchronized void rebind() throws MalformedURLException, RemoteException, AlreadyBoundException {

        registry = LocateRegistry.createRegistry(configure.getTargetPort());
        for (String ip : HostUtils.getAllLocalHostIP()) {
            final String bindName = String.format("rmi://%s:%d/RMI_GREYS_ANATOMY", ip, configure.getTargetPort());
            try {
                info("lookup for : %s", bindName);
                Naming.lookup(bindName);
                bind = true;
            } catch (NotBoundException e) {
                // ֻ��û�а󶨲Ż�ȥ��
                info("rebind : %s", bindName);
                Naming.bind(bindName, this);
            }
        }

    }

    /**
     * �����Naming
     */
    private synchronized void unbind() throws RemoteException, NotBoundException, MalformedURLException {

        for (String ip : HostUtils.getAllLocalHostIP()) {

            final String bindName = String.format("rmi://%s:%d/RMI_GREYS_ANATOMY", ip, configure.getTargetPort());
            try {
                Naming.unbind(bindName);
                info("unbind : %s", bindName);
            } catch (Exception e) {
                debug(e, "unbind failed : %s;", bindName);
            }
        }//for

        if (null != registry) {
            UnicastRemoteObject.unexportObject(registry, true);
        }

        bind = false;

    }

    /**
     * �Ƿ��ѱ�RMI��
     *
     * @return �Ƿ��ѱ�RMI��
     */
    public boolean isBind() {
        return bind;
    }

    /**
     * �ر�ConsoleServer
     *
     * @throws RemoteException
     * @throws NotBoundException
     * @throws MalformedURLException
     */
    public void shutdown() throws RemoteException, NotBoundException, MalformedURLException {
        unbind();
    }


    private static volatile ConsoleServer instance;

    /**
     * ��������̨������<br/>
     * ��AgentMain��ͨ������������
     *
     * @param configure �����ļ�
     * @throws MalformedURLException
     * @throws RemoteException
     */
    public static synchronized ConsoleServer getInstance(Configure configure, Instrumentation inst) throws RemoteException, MalformedURLException, AlreadyBoundException {
        if (null == instance) {
            instance = new ConsoleServer(configure, inst);
            info(GaStringUtils.getLogo());
        }
        return instance;
    }

    @Override
    public RespResult postCmd(ReqCmd cmd) throws Exception {
        return serverHandler.postCmd(cmd);
    }

    @Override
    public long register() throws Exception {
        return serverHandler.register();
    }

    @Override
    public boolean checkPID(int pid) throws Exception {
        return configure.getJavaPid() == pid;
    }

    @Override
    public RespResult getCmdExecuteResult(ReqGetResult req) throws Exception {
        return serverHandler.getCmdExecuteResult(req);
    }

    @Override
    public void killJob(ReqKillJob req) throws Exception {
        serverHandler.killJob(req);
    }

    @Override
    public boolean sessionHeartBeat(ReqHeart req) throws Exception {
        return serverHandler.sessionHeartBeat(req);
    }

    public Configure getConfigure() {
        return configure;
    }
}
