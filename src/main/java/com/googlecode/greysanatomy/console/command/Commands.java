package com.googlecode.greysanatomy.console.command;

import com.googlecode.greysanatomy.console.FileValueConverter;
import com.googlecode.greysanatomy.console.InputCompleter;
import com.googlecode.greysanatomy.console.command.annotation.Cmd;
import com.googlecode.greysanatomy.console.command.annotation.IndexArg;
import com.googlecode.greysanatomy.console.command.annotation.NamedArg;
import com.googlecode.greysanatomy.util.GaReflectUtils;
import jline.console.ConsoleReader;
import jline.console.completer.*;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpecBuilder;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class Commands {

    private final Map<String, Class<?>> commands = new HashMap<String, Class<?>>();

    private Commands() {

        for (Class<?> clazz : GaReflectUtils.getClasses("com.googlecode.greysanatomy.console.command")) {

            if (!Command.class.isAssignableFrom(clazz)
                    || Modifier.isAbstract(clazz.getModifiers())) {
                continue;
            }

            if (clazz.isAnnotationPresent(Cmd.class)) {
                final Cmd cmd = clazz.getAnnotation(Cmd.class);
                commands.put(cmd.named(), clazz);
            }


        }

    }

    /**
     * ��������������������ݹ���һ������
     *
     * @param line �����������һ������
     * @return ������������
     * @throws IllegalAccessException
     * @throws InstantiationException
     */
    public Command newCommand(String line) throws IllegalAccessException, InstantiationException {

        final String[] splitOfLine = line.split("\\s+");
        final String cmdName = splitOfLine[0];
        final Class<?> clazz = getInstance().commands.get(cmdName);
        if (null == clazz) {
            return null;
        }

        final Command command = (Command) clazz.newInstance();
        final OptionSet opt = getOptionParser(clazz).parse(splitOfLine);

        for (final Field field : clazz.getDeclaredFields()) {

            // ������������
            if (field.isAnnotationPresent(NamedArg.class)) {
                final NamedArg arg = field.getAnnotation(NamedArg.class);

                if (arg.hasValue()) {
                    if (opt.has(arg.named())) {
                        Object value = opt.valueOf(arg.named());

                        //�����ö�����ͣ������ö����Ϣ��ֵ
                        if (field.getType().isEnum()) {
                            final Enum<?>[] enums = (Enum[]) field.getType().getEnumConstants();
                            if (enums != null) {
                                for (Enum<?> e : enums) {
                                    if (e.name().equals(value)) {
                                        value = e;
                                        break;
                                    }
                                }
                            }
                        }
                        GaReflectUtils.set(field, value, command);
                    }
                }

                // ����boolean����,һ��ֻ��boolean����hasValue��Ϊfalse
                else {
                    GaReflectUtils.set(field, opt.has(arg.named()), command);
                }


            }

            // ����˳�����
            else if (field.isAnnotationPresent(IndexArg.class)) {
                final IndexArg arg = field.getAnnotation(IndexArg.class);
                final int index = arg.index() + 1;
                if (arg.isRequired()
                        && opt.nonOptionArguments().size() <= index) {
                    throw new IllegalArgumentException(arg.name() + " argument was missing.");
                }

                if (opt.nonOptionArguments().size() > index) {
                    GaReflectUtils.set(field, opt.nonOptionArguments().get(index), command);
                }

            }

        }//for


        return command;
    }

    private static OptionParser getOptionParser(Class<?> clazz) {

        final StringBuilder sb = new StringBuilder();
        for (Field field : clazz.getDeclaredFields()) {
            if (field.isAnnotationPresent(NamedArg.class)) {
                final NamedArg arg = field.getAnnotation(NamedArg.class);
                if (arg.hasValue()) {
                    sb.append(arg.named()).append(":");
                } else {
                    sb.append(arg.named());
                }
            }
        }

        final OptionParser parser
                = sb.length() == 0 ? new OptionParser() : new OptionParser(sb.toString());
        for (Field field : clazz.getDeclaredFields()) {
            if (field.isAnnotationPresent(NamedArg.class)) {
                final NamedArg arg = field.getAnnotation(NamedArg.class);
                if (arg.hasValue()) {
                    final OptionSpecBuilder osb = parser.accepts(arg.named(), arg.description());
                    osb.withOptionalArg()
                            .withValuesConvertedBy(new FileValueConverter())
                            .ofType(field.getType());
                }
            }
        }

        return parser;
    }

    /**
     * �г����о�������
     *
     * @return ���ص�ǰ�汾��֧�ֵľ��������
     */
    public Map<String, Class<?>> listCommands() {
        return new HashMap<String, Class<?>>(commands);
    }


    private Collection<Completer> getCommandCompleterList() {
        final Collection<Completer> completerList = new ArrayList<Completer>();

        for (Map.Entry<String, Class<?>> entry : Commands.getInstance().listCommands().entrySet()) {
            ArgumentCompleter argCompleter = new ArgumentCompleter();
            completerList.add(argCompleter);
            argCompleter.getCompleters().add(new StringsCompleter(entry.getKey()));

            if (entry.getKey().equals("help")) {
                argCompleter.getCompleters().add(new StringsCompleter(Commands.getInstance().listCommands().keySet()));
            }

            for (Field field : GaReflectUtils.getFields(entry.getValue())) {
                if (field.isAnnotationPresent(NamedArg.class)) {
                    NamedArg arg = field.getAnnotation(NamedArg.class);
                    argCompleter.getCompleters().add(new StringsCompleter("-" + arg.named()));
                    if (File.class.isAssignableFrom(field.getType())) {
                        argCompleter.getCompleters().add(new FileNameCompleter());
                    }

//                    // boolean���͵��Ѿ�ͨ���Զ�ʶ��ķ�ʽ���в��䣬������Ҫ�û�������д
//                    else if (Boolean.class.isAssignableFrom(field.getType())
//                            || boolean.class.isAssignableFrom(field.getType())) {
//                        argCompleter.getCompleters().add(new StringsCompleter("true", "false"));
//                    }

                    else if (field.getType().isEnum()) {
                        Enum<?>[] enums = (Enum[]) field.getType().getEnumConstants();
                        String[] enumArgs = new String[enums.length];
                        for (int i = 0; i < enums.length; i++) {
                            enumArgs[i] = enums[i].name();
                        }
                        argCompleter.getCompleters().add(new StringsCompleter(enumArgs));
                    } else {
                        argCompleter.getCompleters().add(new InputCompleter());
                    }
                }
            }//for
            argCompleter.getCompleters().add(new NullCompleter());
        }

        return completerList;
    }

    /**
     * ע����ʾ��Ϣ
     *
     * @param console ����̨
     */
    public void regCompleter(ConsoleReader console) {
        console.addCompleter(new AggregateCompleter(getCommandCompleterList()));

    }

    private static final Commands instance = new Commands();

    public static synchronized Commands getInstance() {
        return instance;
    }

}
