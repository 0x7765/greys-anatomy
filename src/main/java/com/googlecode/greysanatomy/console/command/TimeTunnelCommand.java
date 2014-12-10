package com.googlecode.greysanatomy.console.command;

import com.googlecode.greysanatomy.agent.GreysAnatomyClassFileTransformer;
import com.googlecode.greysanatomy.console.command.annotation.RiscCmd;
import com.googlecode.greysanatomy.console.command.annotation.RiscIndexArg;
import com.googlecode.greysanatomy.console.command.annotation.RiscNamedArg;
import com.googlecode.greysanatomy.console.server.ConsoleServer;
import com.googlecode.greysanatomy.probe.Advice;
import com.googlecode.greysanatomy.probe.AdviceListenerAdapter;
import com.googlecode.greysanatomy.util.GaObjectUtils;
import com.googlecode.greysanatomy.util.GaOgnlUtils;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.instrument.Instrumentation;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.googlecode.greysanatomy.agent.GreysAnatomyClassFileTransformer.transform;
import static com.googlecode.greysanatomy.console.server.SessionJobsHolder.registJob;
import static com.googlecode.greysanatomy.probe.ProbeJobs.activeJob;
import static com.googlecode.greysanatomy.util.GaStringUtils.*;
import static java.lang.String.format;

/**
 * ʱ���������<br/>
 * ����w/d�����ڲ���i�����ݵļ�¼���<br/>
 * Created by vlinux on 14/11/15.
 */
@RiscCmd(named = "tt", sort = 8, desc = "TimeTunnel the method call.",
        eg = {
                "tt -t .*StringUtils isEmpty",
                "tt -l",
                "tt -D",
                "tt -i 1000 -w params[0]",
                "tt -i 1000 -d",
                "tt -i 1000",
//                "tt -i 1000 -p"
        })
public class TimeTunnelCommand extends Command {

    private static final Logger logger = Logger.getLogger("greysanatomy");

    // the TimeTunnels collection
    private static final Map<Integer, TimeTunnel> timeTunnels = new LinkedHashMap<Integer, TimeTunnel>();

    // the TimeTunnel's index sequence
    private static final AtomicInteger sequence = new AtomicInteger(1000);

    // TimeTunnel the method call
    @RiscNamedArg(named = "t", description = "TimeTunnel the method called.")
    private boolean isTimeTunnel = false;

    @RiscIndexArg(index = 0, isRequired = false, name = "class-regex", description = "regex match of classpath.classname")
    private String classRegex;

    @RiscIndexArg(index = 1, isRequired = false, name = "method-regex", description = "regex match of methodname")
    private String methodRegex;

    // list the TimeTunnel
    @RiscNamedArg(named = "l", description = "list all the TimeTunnels.")
    private boolean isList = false;

    @RiscNamedArg(named = "D", description = "delete all TimeTunnels.")
    private boolean isDeleteAll = false;


    // index of TimeTunnel
    @RiscNamedArg(named = "i", hasValue = true, description = "appoint the index of TimeTunnel. If use only, show the TimeTunnel detail.")
    private Integer index;

    // expend of TimeTunnel
    @RiscNamedArg(named = "x", hasValue = true, description = "expend level of object. Default level-0")
    private Integer expend;

    // watch the index TimeTunnel
    @RiscNamedArg(named = "w",
            hasValue = true,
            description = "watch the TimeTunnel's data, like params[0], returnObj, throwExp and so on.",
            description2 = ""
                    + " \n"
                    + "For example\n"
                    + "    : params[0]\n"
                    + "    : params[0]+params[1]\n"
                    + "    : returnObj\n"
                    + "    : throwExp\n"
                    + "    : target.targetThis.getClass()\n"
                    + " \n"
                    + "The structure of 'advice'\n"
                    + "    params[0..n] : the parameters of methods\n"
                    + "    returnObj    : the return object of methods\n"
                    + "    throwExp     : the throw exception of methods\n"
                    + "    target\n"
                    + "    \\+- targetThis  : the object entity\n"
                    + "    \\+- targetClassName : the object's class\n"
                    + "    \\+- targetBehaviorName : the object's class\n"
                    + " \n")
    private String watchExpress = EMPTY;


//    // play the index TimeTunnel
//    @RiscNamedArg(named = "p", description = "play the TimeTunnel of method called.")
//    private boolean isPlay = false;

    // delete the index TimeTunnel
    @RiscNamedArg(named = "d", description = "delete the index TimeTunnel.")
    private boolean isDelete = false;


    /**
     * �������Ƿ�Ϸ�
     */
    private void checkArguments() {

        // ���p/w/d�����Ƿ���i��������
        if (isNotBlank(watchExpress)
//                || isPlay
                || isDelete) {

            if (null == index) {
                throw new IllegalArgumentException("miss TimeTunnel index, please type -i to appoint it.");
            }

        }

        // ��r������class-regex,method-regex��ѡ���ɱ���
        if (isTimeTunnel) {
            if (isBlank(classRegex)) {
                throw new IllegalArgumentException("miss class-regex, please type the regex express to match class.");
            }
            if (isBlank(methodRegex)) {
                throw new IllegalArgumentException("miss method-regex, please type the regex express to match method.");
            }
        }

//        // ���ֻ��i������û�ж�Ӧ��p/w/d������û�������
//        if (null != index) {
//
//            if (StringUtils.isBlank(watchExpress)
////                    && !isPlay
//                    && !isDelete) {
//                throw new IllegalArgumentException("miss arguments to work in with -w/-d .");
//            }
//
//        }

        // һ��������û���ǲ��е�
        if (null == index
                && !isTimeTunnel
                && !isDelete
                && !isDeleteAll
                && isBlank(watchExpress)
                && !isList
//                && !isPlay
                ) {

            throw new IllegalArgumentException("miss arguments, type help TimeTunnel to got usage.");

        }

    }

    /**
     * ����TimeTunnel
     *
     * @param timeTunnel
     * @return
     */
    private int putTimeTunnel(TimeTunnel timeTunnel) {
        final int index = sequence.getAndIncrement();
        timeTunnels.put(index, timeTunnel);
        return index;
    }


    /*
     * ���п��
     */
    private static final int[] TABLE_COL_WIDTH = new int[]{

            8, // index
            20, // timestamp
            8, // isRet
            8, // isExp
            15, // object address
//            20, // class loader
            30, // class
            30, // method

    };

    /*
     * ��������
     */
    private static final String[] TABLE_COL_TITLE = new String[]{

            "INDEX",
            "TIMESTAMP",
            "IS-RET",
            "IS-EXP",
            "OBJECT",
//            "CLASS-LOADER",
            "CLASS",
            "METHOD"

    };

    /**
     * ��ӡ��ͷ
     *
     * @param lineSB
     */
    private void printTableHead(StringBuilder lineSB) {


        final StringBuilder tableHeadSB = new StringBuilder();
        printLineSplit(tableHeadSB);
        final StringBuilder lineFormatSB = new StringBuilder();
        for (int colWidth : TABLE_COL_WIDTH) {
            lineFormatSB.append("|%").append(colWidth).append("s");
        }
        lineFormatSB.append("|");
        tableHeadSB.append(format(lineFormatSB.toString(), TABLE_COL_TITLE)).append("\n");
        printLineSplit(tableHeadSB);
        lineSB.append(tableHeadSB);

    }

    /**
     * ��ӡ�ָ���
     *
     * @param lineSB
     */
    private void printLineSplit(StringBuilder lineSB) {
        final StringBuilder lineSplitSB = new StringBuilder();
        for (int colWidth : TABLE_COL_WIDTH) {
            lineSplitSB.append("+").append(repeat("-", colWidth));
        }
        lineSplitSB.append("+").append("\n");
        lineSB.append(lineSplitSB);
    }

    /**
     * ��ӡ��¼
     *
     * @param lineSB
     * @param index
     * @param timeTunnel
     */
    private void printTimeTunnel(StringBuilder lineSB, int index, TimeTunnel timeTunnel) {

        final StringBuilder lineFormatSB = new StringBuilder();
        for (int colWidth : TABLE_COL_WIDTH) {
            lineFormatSB.append("|%-").append(colWidth).append("s");
        }
        lineFormatSB.append("|");
        final String lineFormat = lineFormatSB.toString();
        final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        lineSB.append(format(lineFormat,
                index,
                sdf.format(timeTunnel.getGmtCreate()),
                timeTunnel.getAdvice().isReturn(),
                timeTunnel.getAdvice().isThrowException(),
                timeTunnel.getAdvice().getTarget().getTargetThis() == null ? "NULL" : "0x" + Integer.toHexString(timeTunnel.getAdvice().getTarget().getTargetThis().hashCode()),
//                substring(TimeTunnel.getTargetClassLoader().getClass().getSimpleName(), 0, TABLE_COL_WIDTH[4]),
                summary(substring(substringAfterLast(timeTunnel.getAdvice().getTarget().getTargetClassName(), "."), 0, TABLE_COL_WIDTH[5]), TABLE_COL_WIDTH[5]),
                summary(substring(timeTunnel.getAdvice().getTarget().getTargetBehaviorName(), 0, TABLE_COL_WIDTH[6]), TABLE_COL_WIDTH[6])
        )).append("\n");

    }

    /**
     * do the TimeTunnel command
     *
     * @param info
     * @param sender
     * @throws Throwable
     */
    private void doTimeTunnel(final Info info, final Sender sender) throws Throwable {

        final Instrumentation inst = info.getInst();
        final GreysAnatomyClassFileTransformer.TransformResult result = transform(inst, classRegex, methodRegex, new AdviceListenerAdapter() {

            boolean isFirst = true;

            @Override
            public void onFinish(Advice advice) {

                try {

                    final TimeTunnel timeTunnel = new TimeTunnel(advice, new Date());
                    final int index = putTimeTunnel(timeTunnel);

                    final StringBuilder lineSB = new StringBuilder();
                    if (isFirst) {
                        // output the title
                        isFirst = false;
                        printTableHead(lineSB);
                    }

                    printTimeTunnel(lineSB, index, timeTunnel);

                    sender.send(false, lineSB.toString());

                } catch (Throwable t) {
                    if (logger.isLoggable(Level.WARNING)) {
                        logger.log(Level.WARNING, "TimeTunnel failed.", t);
                    }
                }

            }
        }, info);

        // ע������
        registJob(info.getSessionId(), result.getId());

        // ��������
        activeJob(result.getId());

        final StringBuilder message = new StringBuilder();
        message.append(LINE);
        message.append(format("done. probe:c-Cnt=%s,m-Cnt=%s\n",
                result.getModifiedClasses().size(),
                result.getModifiedBehaviors().size()));
        message.append(ABORT_MSG).append("\n");
        sender.send(false, message.toString());

    }


    /**
     * do list timeTunnels
     *
     * @param sender
     * @throws Throwable
     */
    private void doList(final Sender sender) throws Throwable {

        final StringBuilder lineSB = new StringBuilder();
        if (timeTunnels.isEmpty()) {
            lineSB.append("timeTunnels is empty.\n");
        } else {
            printTableHead(lineSB);
            for (Map.Entry<Integer, TimeTunnel> entry : timeTunnels.entrySet()) {
                printTimeTunnel(lineSB, entry.getKey(), entry.getValue());
            }
            printLineSplit(lineSB);

        }

        sender.send(true, lineSB.toString());

    }

    /**
     * ������еļ�¼
     *
     * @param sender
     */
    private void doDeleteAll(final Sender sender) {
        timeTunnels.clear();
        sender.send(true, "All timeTunnels was deleted.\n");
    }

    /**
     * �鿴��¼��Ϣ
     *
     * @param sender
     * @throws Throwable
     */
    private void doWatch(final Sender sender) throws Throwable {

        // find the TimeTunnel
        final TimeTunnel timeTunnel = timeTunnels.get(index);
        if (null == timeTunnel) {
            sender.send(true, format("TimeTunnel %s not found.", index));
            return;
        }

//        final ScriptEngine jsEngine = new ScriptEngineManager().getEngineByExtension("js");
//
//        jsEngine.eval("function printWatch(p,o){try{o.send(true, " + watchExpress + "+'\\n');}catch(e){o.send(true, e.message+'\\n');}}");
//        final Invocable invoke = (Invocable) jsEngine;
//        final Advice p = timeTunnel.getAdvice();
//        invoke.invokeFunction("printWatch", p, sender);

        final Advice p = timeTunnel.getAdvice();
        final Object value = GaOgnlUtils.getValue(watchExpress, p);

        if( null != expend
                && expend > 0) {
            sender.send(true, "" + GaObjectUtils.toString(value, 0, expend) + "\n");
        } else {
            sender.send(true, "" + value + "\n");
        }



    }


    /**
     * ɾ��ָ����¼
     *
     * @param sender
     */
    private void doDelete(final Sender sender) {

        timeTunnels.remove(index);
        sender.send(true, format("delete %s successed.", index));

    }

//    /**
//     * ִ�лطŲ���
//     *
//     * @param sender
//     * @throws Throwable
//     */
//    private void doPlay(final Sender sender) throws Throwable {
//
//        // find the record
//        final Record record = timeTunnels.get(index);
//        if (null == record) {
//            sender.send(true, format("record %s not found.", index));
//            return;
//        }
//
//        try {
//            final Method method = record.getTargetMethod();
//            method.invoke(record.getTargetThis(), record.getParameters());
//        } catch (Throwable t) {
//            // do nothing...
//            logger.info("play record {} got an exception.", index, t);
//        } finally {
//            sender.send(true, format("play record %s done.", index));
//        }
//
//    }


    /**
     * չʾָ����¼
     *
     * @param sender
     */
    private void doShow(final Sender sender) {

        // find the TimeTunnel
        final TimeTunnel timeTunnel = timeTunnels.get(index);
        if (null == timeTunnel) {
            sender.send(true, format("TimeTunnel %s not found.", index));
            return;
        }

        final String className = timeTunnel.getAdvice().getTarget().getTargetClassName();
        final String methodName = timeTunnel.getAdvice().getTarget().getTargetBehaviorName();
        final String objectAddress = timeTunnel.getAdvice().getTarget().getTargetThis() == null ? "NULL" : "0x" + Integer.toHexString(timeTunnel.getAdvice().getTarget().getTargetThis().hashCode());
        final int maxColLen = Math.max(Math.max(className.length(), methodName.length()), 50);

        final StringBuilder detailSB = new StringBuilder();
        final String headFormat = "|%20s|%-" + maxColLen + "s|";
        final String lineSplit = new StringBuilder()
                .append("+").append(repeat("-", 20))
                .append("+").append(repeat("-", maxColLen))
                .append("+\n")
                .toString();

        final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

        // fill the head
        detailSB.append(lineSplit)
                .append(format(headFormat, "INDEX: ", index)).append("\n")
                .append(format(headFormat, "GMT-CREATE: ", sdf.format(timeTunnel.getGmtCreate()))).append("\n")
                .append(format(headFormat, "IS-RETURN: ", timeTunnel.getAdvice().isReturn())).append("\n")
                .append(format(headFormat, "IS-EXCEPTION: ", timeTunnel.getAdvice().isThrowException())).append("\n")
                .append(format(headFormat, "OBJECT: ", objectAddress)).append("\n")
                .append(format(headFormat, "CLASS: ", className)).append("\n")
                .append(format(headFormat, "METHOD: ", methodName)).append("\n")
                .append(lineSplit)
                .append("\n");


        // fill the paramenters
        if (null != timeTunnel.getAdvice().getParameters()) {

            int paramIndex = 0;
            for (Object param : timeTunnel.getAdvice().getParameters()) {

                if( null != expend
                        && expend > 0) {
                    detailSB.append("PARAMETERS[" + paramIndex++ + "]:\n")
                            .append(GaObjectUtils.toString(param, 0, expend))
                            .append("\n\n");
                } else {
                    detailSB.append("PARAMETERS[" + paramIndex++ + "]:\n").append(param).append("\n\n");
                }

            }

        }


        // fill the returnObj
        if (timeTunnel.getAdvice().isReturn()) {

            if( null != expend
                    && expend > 0) {
                detailSB.append("RETURN-OBJ:\n")
                        .append(GaObjectUtils.toString(timeTunnel.getAdvice().getReturnObj(),0,expend))
                        .append("\n\n");
            } else {
                detailSB.append("RETURN-OBJ:\n").append(timeTunnel.getAdvice().getReturnObj()).append("\n\n");
            }

        }


        // fill the throw exception
        if (timeTunnel.getAdvice().isThrowException()) {
            final Throwable throwable = timeTunnel.getAdvice().getThrowException();

            if( null != expend
                    && expend > 0) {
                detailSB.append("THROW-EXCEPTION:\n")
                        .append(GaObjectUtils.toString(throwable, 0, expend))
                        .append("\n\n");
            } else {
                final StringWriter stringWriter = new StringWriter();
                final PrintWriter printWriter = new PrintWriter(stringWriter);
                throwable.printStackTrace(printWriter);
                detailSB.append("THROW-EXCEPTION:\n").append(stringWriter.toString()).append("\n\n");
            }

        }

        sender.send(true, detailSB.toString());

    }

    @Override
    public Action getAction() {
        return new Action() {

            @Override
            public void action(ConsoleServer consoleServer, final Info info, final Sender sender) throws Throwable {

                // ������
                checkArguments();


                // TimeTunnel ����
                if (isTimeTunnel) {
                    doTimeTunnel(info, sender);
                } else if (isList) {

                    // list ����
                    doList(sender);

                } else if (isDeleteAll) {

                    // delete all the TimeTunnel
                    doDeleteAll(sender);

                } else if (isNotBlank(watchExpress)) {

                    // watch TimeTunnel by js express
                    doWatch(sender);

                } else if (isDelete) {

                    // delete index TimeTunnel
                    doDelete(sender);

                } else if (null != index) {

                    // show the TimeTunnel
                    doShow(sender);

                }
//                else if (isPlay) {
//
//                    // play the TimeTunnel
//                    doPlay(sender);
//
//                }


            }

        };
    }

}


/**
 * ̽���¼�������ü�¼
 */
class TimeTunnel {

    public TimeTunnel(Advice advice, Date gmtCreate) {
        this.advice = advice;
        this.gmtCreate = gmtCreate;
    }

    private final Advice advice;
    private final Date gmtCreate;

    public Advice getAdvice() {
        return advice;
    }

    public Date getGmtCreate() {
        return gmtCreate;
    }
}