package com.googlecode.greysanatomy.console;

import com.googlecode.greysanatomy.Configer;
import com.googlecode.greysanatomy.console.command.Command;
import com.googlecode.greysanatomy.console.command.Commands;
import com.googlecode.greysanatomy.console.command.QuitCommand;
import com.googlecode.greysanatomy.console.command.ShutdownCommand;
import com.googlecode.greysanatomy.console.rmi.RespResult;
import com.googlecode.greysanatomy.console.rmi.req.ReqCmd;
import com.googlecode.greysanatomy.console.rmi.req.ReqGetResult;
import com.googlecode.greysanatomy.console.rmi.req.ReqKillJob;
import com.googlecode.greysanatomy.console.server.ConsoleServerService;
import com.googlecode.greysanatomy.exception.ConsoleException;
import com.googlecode.greysanatomy.util.GaStringUtils;
import jline.console.ConsoleReader;
import jline.console.KeyMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.io.Writer;
import java.rmi.NoSuchObjectException;

import static com.googlecode.greysanatomy.util.GaStringUtils.isBlank;
import static com.googlecode.greysanatomy.util.GaStringUtils.EMPTY;


/**
 * ����̨
 *
 * @author vlinux
 */
public class GreysAnatomyConsole {

    private static final Logger logger = LoggerFactory.getLogger("greysanatomy");

    private final Configer configer;
    private final ConsoleReader console;

    private volatile boolean isF = true;
    private volatile boolean isQuit = false;

    private final long sessionId;
    private int jobId;

    /**
     * ����GA����̨
     *
     * @param configer
     * @throws IOException
     */
    public GreysAnatomyConsole(Configer configer, long sessionId) throws IOException {
        this.console = new ConsoleReader(System.in, System.out);
        this.configer = configer;
        this.sessionId = sessionId;
        write(GaStringUtils.getLogo());
        Commands.getInstance().registCompleter(console);
    }

    /**
     * ����̨������
     *
     * @author vlinux
     */
    private class GaConsoleInputer implements Runnable {

        private final ConsoleServerService consoleServer;

        private GaConsoleInputer(ConsoleServerService consoleServer) {
            this.consoleServer = consoleServer;
        }

        @Override
        public void run() {
            while (true) {
                try {
                    //����̨������
                    doRead();
                } catch (ConsoleException ce) {
                    write("Error : " + ce.getMessage() + "\n");
                    write("Please type help for more information...\n\n");
                } catch (Exception e) {
                    // �����ǿ���̨������ô��
                    logger.warn("console read failed.", e);
                }
            }
        }

        private void doRead() throws Exception {
            final String prompt = isF ? configer.getConsolePrompt() : EMPTY;
            final ReqCmd reqCmd = new ReqCmd(console.readLine(prompt), sessionId);

			/*
             * ���������ǿհ��ַ������ߵ�ǰ����̨û�����Ϊ�����
			 * �������������ȡ����
			 */
            if (isBlank(reqCmd.getCommand()) || !isF) {
                return;
            }

            final Command command;
            try {
                command = Commands.getInstance().newRiscCommand(reqCmd.getCommand());
            } catch (Exception e) {
                throw new ConsoleException(e.getMessage());
            }

            // ������״̬���Ϊδ���
            isF = false;

            // �û�ִ����һ��shutdown����,�ն���Ҫ�˳�
            if (command instanceof ShutdownCommand
                    || command instanceof QuitCommand) {
                isQuit = true;
            }


            // ������������
            RespResult result = consoleServer.postCmd(reqCmd);
            jobId = result.getJobId();
        }

    }

    /**
     * ����̨�����
     *
     * @author chengtongda
     */
    private class GaConsoleOutputer implements Runnable {

        private final ConsoleServerService consoleServer;
        private int currentJob;
        private int pos = 0;

        private GaConsoleOutputer(ConsoleServerService consoleServer) {
            this.consoleServer = consoleServer;
        }

        @Override
        public void run() {
            while (true) {
                try {
                    //����̨д����
                    doWrite();
                    //ÿ500ms��һ�ν��
                    Thread.sleep(500);
                } catch (NoSuchObjectException nsoe) {
                    // Ŀ��RMI�ر�,��Ҫ�˳�����̨
                    logger.warn("target RMI's server was closed, console will be exit.");
                    break;
                } catch (Exception e) {
                    logger.warn("console write failed.", e);
                }
            }
        }

        private void doWrite() throws Exception {
            //��������������û��ע���job  �򲻶�
            if (isF
                    || sessionId == 0
                    || jobId == 0) {
                return;
            }

            //�����ǰ��ȡ�����job��������ִ�е�job�����0��ʼ��
            if (currentJob != jobId) {
                pos = 0;
                currentJob = jobId;
            }

            RespResult resp = consoleServer.getCmdExecuteResult(new ReqGetResult(jobId, sessionId, pos));
            pos = resp.getPos();

            write(resp);

            if (isQuit) {
                logger.info("greys console will be shutdown.");
                System.exit(0);
            }

        }

    }

    /**
     * ����console
     *
     * @param consoleServer RMIͨѶ�õ�ConsoleServer
     */
    public synchronized void start(final ConsoleServerService consoleServer) {
        this.console.getKeys().bind("" + KeyMap.CTRL_D, new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e) {
                if (!isF) {
                    try {
                        isF = true;
                        write("abort it.\n");
                        redrawLine();
                        consoleServer.killJob(new ReqKillJob(sessionId, jobId));
                    } catch (Exception e1) {
                        // �����ǿ���̨������ô��
                        logger.warn("killJob failed.", e);
                    }
                }
            }

        });
        new Thread(new GaConsoleInputer(consoleServer), "ga-console-inputer").start();
        new Thread(new GaConsoleOutputer(consoleServer), "ga-console-outputer").start();
    }

    private synchronized void redrawLine() throws IOException {
        final String prompt = isF ? configer.getConsolePrompt() : EMPTY;
        console.setPrompt(prompt);
        console.redrawLine();
        console.flush();
    }

    /**
     * �����̨���������Ϣ
     *
     * @param resp ���ر�����Ϣ
     */
    private void write(RespResult resp) throws IOException {
        if (!isF) {
            String content = resp.getMessage();
            if (resp.isFinish()) {
                isF = true;
                //content += "\n------------------------------end------------------------------\n";
                content += "\n";
            }
            if (!GaStringUtils.isEmpty(content)) {
                write(content);
                redrawLine();
            }
        }
    }

    /**
     * �����Ϣ
     *
     * @param message ����ı�����
     */
    private void write(String message) {
        final Writer writer = console.getOutput();
        try {
            writer.write(message);
            writer.flush();
        } catch (IOException e) {
            // ����̨дʧ�ܣ�����ô��
            logger.warn("console write failed.", e);
        }

    }

}
