package com.googlecode.greysanatomy.console.command;

import com.googlecode.greysanatomy.console.FileValueConverter;
import com.googlecode.greysanatomy.console.InputCompleter;
import com.googlecode.greysanatomy.console.command.annotation.RiscCmd;
import com.googlecode.greysanatomy.console.command.annotation.RiscIndexArg;
import com.googlecode.greysanatomy.console.command.annotation.RiscNamedArg;
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

    private final Map<String, Class<?>> riscCommands = new HashMap<String, Class<?>>();

    private Commands() {

        for (Class<?> clazz : GaReflectUtils.getClasses("com.googlecode.greysanatomy.console.command")) {

            if (!Command.class.isAssignableFrom(clazz)
                    || Modifier.isAbstract(clazz.getModifiers())) {
                continue;
            }

            if (clazz.isAnnotationPresent(RiscCmd.class)) {
                final RiscCmd cmd = clazz.getAnnotation(RiscCmd.class);
                riscCommands.put(cmd.named(), clazz);
            }


        }

    }

    public Command newRiscCommand(String line) throws IllegalAccessException, InstantiationException {

        final String[] strs = line.split("\\s+");
        final String cmdName = strs[0];
        final Class<?> clazz = getInstance().riscCommands.get(cmdName);
        if (null == clazz) {
            return null;
        }

        final Command command = (Command) clazz.newInstance();
        final OptionSet opt = getRiscOptionParser(clazz).parse(strs);

        for (Field field : clazz.getDeclaredFields()) {
            if (field.isAnnotationPresent(RiscNamedArg.class)) {
                final RiscNamedArg arg = field.getAnnotation(RiscNamedArg.class);

                if (arg.hasValue()) {
                    if (opt.has(arg.named())) {
                        Object value = opt.valueOf(arg.named());

                        //�����ö�����ͣ������ö����Ϣ��ֵ
                        if (field.getType().isEnum()) {
                            Enum<?>[] enums = (Enum[]) field.getType().getEnumConstants();
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
                } else {

                    GaReflectUtils.set(field, opt.has(arg.named()), command);

                }


            } else if (field.isAnnotationPresent(RiscIndexArg.class)) {
                final RiscIndexArg arg = field.getAnnotation(RiscIndexArg.class);
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

    private static OptionParser getRiscOptionParser(Class<?> clazz) {

        final StringBuilder sb = new StringBuilder();
        for (Field field : clazz.getDeclaredFields()) {
            if (field.isAnnotationPresent(RiscNamedArg.class)) {
                final RiscNamedArg arg = field.getAnnotation(RiscNamedArg.class);
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
            if (field.isAnnotationPresent(RiscNamedArg.class)) {
                final RiscNamedArg arg = field.getAnnotation(RiscNamedArg.class);
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
     * @return
     */
    public Map<String, Class<?>> listRiscCommands() {
        return new HashMap<String, Class<?>>(riscCommands);
    }


    private Collection<Completer> getRiscCommandCompleters() {
        final Collection<Completer> completers = new ArrayList<Completer>();

        for (Map.Entry<String, Class<?>> entry : Commands.getInstance().listRiscCommands().entrySet()) {
            ArgumentCompleter argCompleter = new ArgumentCompleter();
            completers.add(argCompleter);
            argCompleter.getCompleters().add(new StringsCompleter(entry.getKey()));

            if (entry.getKey().equals("help")) {
                argCompleter.getCompleters().add(new StringsCompleter(Commands.getInstance().listRiscCommands().keySet()));
            }

            for (Field field : GaReflectUtils.getFields(entry.getValue())) {
                if (field.isAnnotationPresent(RiscNamedArg.class)) {
                    RiscNamedArg arg = field.getAnnotation(RiscNamedArg.class);
                    argCompleter.getCompleters().add(new StringsCompleter("-" + arg.named()));
                    if (File.class.isAssignableFrom(field.getType())) {
                        argCompleter.getCompleters().add(new FileNameCompleter());
                    } else if (Boolean.class.isAssignableFrom(field.getType())
                            || boolean.class.isAssignableFrom(field.getType())) {
//                        argCompleter.getCompleters().add(new StringsCompleter("true", "false"));
                    } else if (field.getType().isEnum()) {
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

        return completers;
    }

    /**
     * ע����ʾ��Ϣ
     *
     * @param console
     */
    public void registCompleter(ConsoleReader console) {
        console.addCompleter(new AggregateCompleter(getRiscCommandCompleters()));

    }

    private static final Commands instance = new Commands();

    /**
     * ��ȡ����
     *
     * @return
     */
    public static synchronized Commands getInstance() {
        return instance;
    }


}
