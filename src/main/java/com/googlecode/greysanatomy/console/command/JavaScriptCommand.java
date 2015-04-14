package com.googlecode.greysanatomy.console.command;

/**
 * javascript������ǿ
 *
 * @author vlinux
 */
//@RiscCmd(named = "js", sort = 3, desc = "Let Greys use the JavaScript enhancement.",
//        eg = {
//                "js -f /tmp/debug.js org\\.apache\\.commons\\.lang\\.StringUtils isBlank",
//                "js -f /tmp/debug.js .*StringUtils isEmpty",
//        })
@Deprecated
public class JavaScriptCommand /*extends Command*/ {



//    @IndexArg(index = 0, name = "class-regex", description = "regex matching of classpath.classname")
//    private String classRegex;
//
//    @IndexArg(index = 1, name = "method-regex", description = "regex matching of methodname")
//    private String methodRegex;
//
//    @NamedArg(named = "f", hasValue = true, description = "the file of javascript",
//            description2 = ""
//                    + " \n"
//                    + "For example\n"
//                    + "    function before(p,o)\n"
//                    + "    {\n"
//                    + "        o.println(p.target.targetBehaviorName+': '+p.parameters[0]+'\\n');\n"
//                    + "    }\n"
//                    + " \n"
//                    + "The functions of js:\n"
//                    + "    function before(p,o);    // method call at each before\n"
//                    + "    function success(p,o);   // method call at each success\n"
//                    + "    function exception(p,o); // method call at each exception\n"
//                    + "    function finish(p,o);    // method call at each finished\n"
//                    + " \n"
//                    + "The structure of arguments 'p'\n"
//                    + "    p.\n"
//                    + "    \\+- params[0..n] : the parameters of methods\n"
//                    + "    \\+- returnObj    : the return object of methods\n"
//                    + "    \\+- throwExp     : the throw exception of methods\n"
//                    + "    \\+- target\n"
//                    + "         \\+- targetThis  : the object entity\n"
//                    + "         \\+- targetClassName : the object's class\n"
//                    + "         \\+- targetBehaviorName : the object's class\n"
//                    + " \n"
//                    + "The method sign of arguments 'o'\n"
//                    + "    o.print(java.lang.String);\n"
//                    + "    o.println(java.lang.String);\n"
//                    + " \n"
//    )
//    private File scriptFile;
//
//    public static interface ScriptBeforeListener {
//        void before(Advice p);
//    }
//
//    public static interface ScriptSuccessListener {
//        void success(Advice p);
//    }
//
//    public static interface ScriptExceptionListener {
//        void exception(Advice p);
//    }
//
//    public static interface ScriptFinishListener {
//        void finish(Advice p);
//    }
//
//    public static interface ScriptCreateListener {
//        void create();
//    }
//
//    public static interface ScriptDestroyListener {
//        void destroy();
//    }
//
//    @Override
//    public Action getAction() {
//        return new Action() {
//
//            @Override
//            public void action(final ConsoleServer consoleServer, final Info info, final Sender sender) throws Throwable {
//
//                if (!scriptFile.isFile()
//                        || !scriptFile.exists()
//                        || !scriptFile.canRead()) {
//                    sender.send(true, "script file not exist.");
//                    return;
//                }
//
//                final ScriptEngine jsEngine = new ScriptEngineManager().getEngineByExtension("js");
//                final Invocable invoke = (Invocable) jsEngine;
//
//                final ScriptBeforeListener scriptBeforeListener;
//                final ScriptSuccessListener scriptSuccessListener;
//                final ScriptExceptionListener scriptExceptionListener;
//                final ScriptFinishListener scriptFinishListener;
//                final ScriptCreateListener scriptCreateListener;
//                final ScriptDestroyListener scriptDestroyListener;
//
//                try {
//
//                    jsEngine.getContext().setWriter(new Writer() {
//                        @Override
//                        public void write(char[] cbuf, int off, int len) throws IOException {
//
//                            final char[] subCbuf = new char[len];
//                            System.arraycopy(cbuf, off, subCbuf, 0, len);
//                            sender.send(false, String.valueOf(subCbuf));
//
//                        }
//
//                        @Override
//                        public void flush() throws IOException {
//
//                        }
//
//                        @Override
//                        public void close() throws IOException {
//
//                        }
//                    });
//
//                    jsEngine.eval(new FileReader(scriptFile));
//
//                    scriptBeforeListener = invoke.getInterface(ScriptBeforeListener.class);
//                    scriptSuccessListener = invoke.getInterface(ScriptSuccessListener.class);
//                    scriptExceptionListener = invoke.getInterface(ScriptExceptionListener.class);
//                    scriptFinishListener = invoke.getInterface(ScriptFinishListener.class);
//                    scriptCreateListener = invoke.getInterface(ScriptCreateListener.class);
//                    scriptDestroyListener = invoke.getInterface(ScriptDestroyListener.class);
//
//                } catch (FileNotFoundException e) {
//                    final String msg = "script file not exist.";
//                    if (logger.isLoggable(Level.WARNING)) {
//                        logger.log(Level.WARNING, msg, e);
//                    }
//                    sender.send(true, msg);
//                    return;
//                } catch (ScriptException e) {
//                    final String msg = "script execute failed." + e.getMessage();
//                    if (logger.isLoggable(Level.WARNING)) {
//                        logger.log(Level.WARNING, msg, e);
//                    }
//                    sender.send(true, msg);
//                    return;
//                }
//
//                final Instrumentation inst = info.getInst();
//                final TransformResult result = transform(inst, classRegex, methodRegex, new AdviceListenerAdapter() {
//
//                    @Override
//                    public void onBefore(final Advice p) {
//                        try {
//                            if (null != scriptBeforeListener) {
//                                scriptBeforeListener.before(p);
//                            }
//                        } catch (Throwable t) {
//                            sender.send(false, t.getMessage());
//                        }
//                    }
//
//                    @Override
//                    public void onSuccess(final Advice p) {
//                        try {
//                            if (null != scriptSuccessListener) {
//                                scriptSuccessListener.success(p);
//                            }
//                        } catch (Throwable t) {
//                            sender.send(false, t.getMessage());
//                        }
//                    }
//
//                    @Override
//                    public void onException(final Advice p) {
//                        try {
//                            if (null != scriptExceptionListener) {
//                                scriptExceptionListener.exception(p);
//                            }
//                        } catch (Throwable t) {
//                            sender.send(false, t.getMessage());
//                        }
//                    }
//
//                    @Override
//                    public void onFinish(final Advice p) {
//                        try {
//                            if (null != scriptFinishListener) {
//                                scriptFinishListener.finish(p);
//                            }
//                        } catch (Throwable t) {
//                            sender.send(false, t.getMessage());
//                        }
//                    }
//
//                    @Override
//                    public void create() {
//                        try {
//                            if (null != scriptCreateListener) {
//                                scriptCreateListener.create();
//                            }
//                        } catch (Throwable t) {
//                            sender.send(false, t.getMessage());
//                        }
//                    }
//
//                    @Override
//                    public void destroy() {
//                        try {
//                            if (null != scriptDestroyListener) {
//                                scriptDestroyListener.destroy();
//                            }
//                        } catch (Throwable t) {
//                            sender.send(false, t.getMessage());
//                        }
//                    }
//
//                }, info ,false);
//
//                // ע������
//                regJob(info.getSessionId(), result.getId());
//
//                // ��������
//                activeJob(result.getId());
//
//                final StringBuilder message = new StringBuilder();
//                message.append(GaStringUtils.LINE);
//                message.append(String.format("done. probe:c-Cnt=%s,m-Cnt=%s\n",
//                        result.getModifiedClasses().size(),
//                        result.getModifiedBehaviors().size()));
//                message.append(GaStringUtils.ABORT_MSG).append("\n");
//                sender.send(false, message.toString());
//            }
//
//        };
//    }

}
