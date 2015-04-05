package com.googlecode.greysanatomy.agent;

import com.googlecode.greysanatomy.Configure;
import com.googlecode.greysanatomy.GreysAnatomyMain;
import com.googlecode.greysanatomy.console.server.ConsoleServer;

import java.lang.instrument.Instrumentation;
import java.net.URL;
import java.net.URLClassLoader;

public class AgentMain {

    public static void premain(String args, Instrumentation inst) {
        main(args, inst);
    }

    public static void agentmain(String args, Instrumentation inst) {
        main(args, inst);
    }

    public static synchronized void main(final String args, final Instrumentation inst) {
        try {

            // ���￼�����Ƿ�Ҫ�ƻ�˫��ί��
            URLClassLoader agentLoader = new URLClassLoader(new URL[]{new URL("file:" + GreysAnatomyMain.JARFILE)});

            final Configure configure = Configure.toConfigure(args);
            final ConsoleServer consoleServer = (ConsoleServer) agentLoader
                    .loadClass("com.googlecode.greysanatomy.console.server.ConsoleServer")
                    .getMethod("getInstance", Configure.class, Instrumentation.class)
                    .invoke(null, configure, inst);

            if (!consoleServer.isBind()) {
                consoleServer.getConfigure().setTargetPort(configure.getTargetPort());
                consoleServer.rebind();
            }

        } catch (Throwable t) {
            t.printStackTrace();
        }

    }

}
