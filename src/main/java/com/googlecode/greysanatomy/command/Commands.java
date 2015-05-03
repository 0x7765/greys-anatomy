package com.googlecode.greysanatomy.command;

import com.googlecode.greysanatomy.command.annotation.Cmd;
import com.googlecode.greysanatomy.command.annotation.IndexArg;
import com.googlecode.greysanatomy.command.annotation.NamedArg;
import com.googlecode.greysanatomy.exception.CommandException;
import com.googlecode.greysanatomy.exception.CommandInitializationException;
import com.googlecode.greysanatomy.exception.CommandNotFoundException;
import com.googlecode.greysanatomy.util.GaReflectUtils;
import com.googlecode.greysanatomy.util.GaStringUtils;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpecBuilder;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;

public class Commands {

    private final Map<String, Class<?>> commands = new HashMap<String, Class<?>>();

    private Commands() {

        for (Class<?> clazz : GaReflectUtils.getClasses("com.googlecode.greysanatomy.command")) {

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
     * @throws CommandNotFoundException       �������
     * @throws CommandInitializationException �����ʼ��ʧ��
     * @throws CommandException               ����ʧ��(����У���)
     */
    public Command newCommand(String line) throws CommandNotFoundException, CommandInitializationException, CommandException {

        final String[] splitOfLine =
                GaStringUtils.splitForArgument(line);
        final String cmdName = splitOfLine[0];
        final Class<?> clazz = getInstance().commands.get(cmdName);
        if (null == clazz) {
            throw new CommandNotFoundException(cmdName);
        }

        final Command command;
        try {
            command = (Command) clazz.newInstance();
        } catch (Throwable t) {
            throw new CommandInitializationException(cmdName, t);
        }


        try {
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
                            try {
                                GaReflectUtils.set(field, value, command);
                            } catch (IllegalAccessException e) {
                                throw new CommandInitializationException(cmdName, e);
                            }
                        }
                    }

                    // ����boolean����,һ��ֻ��boolean����hasValue��Ϊfalse
                    else {
                        try {
                            GaReflectUtils.set(field, opt.has(arg.named()), command);
                        } catch (IllegalAccessException e) {
                            throw new CommandInitializationException(cmdName, e);
                        }
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
                        try {
                            GaReflectUtils.set(field, opt.nonOptionArguments().get(index), command);
                        } catch (IllegalAccessException e) {
                            throw new CommandInitializationException(cmdName, e);
                        }
                    }

                }

            }//for
        } catch (Throwable t) {
            throw new CommandException(cmdName, t);
        }

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
//                            .withValuesConvertedBy(new FileValueConverter())
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

    private static final Commands instance = new Commands();

    public static synchronized Commands getInstance() {
        return instance;
    }

}
