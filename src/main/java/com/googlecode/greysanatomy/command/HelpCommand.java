package com.googlecode.greysanatomy.command;

import com.googlecode.greysanatomy.command.annotation.Cmd;
import com.googlecode.greysanatomy.command.annotation.IndexArg;
import com.googlecode.greysanatomy.command.annotation.NamedArg;
import com.googlecode.greysanatomy.command.view.TableView;
import com.googlecode.greysanatomy.command.view.TableView.ColumnDefine;
import com.googlecode.greysanatomy.server.GaSession;

import java.lang.reflect.Field;
import java.util.*;

import static com.googlecode.greysanatomy.command.view.TableView.Align.LEFT;
import static com.googlecode.greysanatomy.command.view.TableView.Align.RIGHT;
import static com.googlecode.greysanatomy.util.GaStringUtils.isBlank;
import static com.googlecode.greysanatomy.util.GaStringUtils.split;
import static java.lang.String.format;

/**
 * ��������<br/>
 * �����Ĵ�����һB�����Ҷ����뿴
 * <p/>
 * Created by vlinux on 14/10/26.
 */
@Cmd(named = "help", sort = 12, desc = "List of the Greys command list.",
        eg = {
                "help",
                "help sc",
                "help sm",
                "help watch"
        })
public class HelpCommand extends Command {

    @IndexArg(index = 0, isRequired = false, name = "command-name", summary = "the name of command")
    private String cmd;

    @Override
    public Action getAction() {
        return new Action() {

            @Override
            public void action(final GaSession gaSession, final Info info, final Sender sender) throws Throwable {

                if (isBlank(cmd)
                        || !Commands.getInstance().listCommands().containsKey(cmd)) {
                    sender.send(true, mainHelp());
                } else {

                    final Class<?> clazz = Commands.getInstance().listCommands().get(cmd);
                    sender.send(true, commandHelp(clazz));

                }

            }

        };
    }

    private String commandHelp(Class<?> clazz) {
        final Cmd cmd = clazz.getAnnotation(Cmd.class);
        final StringBuilder sb = new StringBuilder("\nUseage:\n\n");

        sb.append("\t");

        final StringBuilder sbOp = new StringBuilder();
        for (Field f : clazz.getDeclaredFields()) {

            if (f.isAnnotationPresent(NamedArg.class)) {
                final NamedArg namedArg = f.getAnnotation(NamedArg.class);
                sbOp.append(namedArg.named());
                if (namedArg.hasValue()) {
                    sbOp.append(":");
                }
            }

        }
        if (sbOp.length() > 0) {
            sb.append("-[").append(sbOp).append("]").append(" ");
        }

        for (Field f : clazz.getDeclaredFields()) {
            if (f.isAnnotationPresent(IndexArg.class)) {
                final IndexArg indexArg = f.getAnnotation(IndexArg.class);
                sb.append(indexArg.name()).append(" ");
            }
        }

        sb.append("\n");
        sb.append("\t").append(cmd.desc()).append("\n\n");


        int maxCol = 10;
        boolean hasOptions = false;
        for (Field f : clazz.getDeclaredFields()) {
            if (f.isAnnotationPresent(IndexArg.class)) {
                final IndexArg indexArg = f.getAnnotation(IndexArg.class);
                maxCol = Math.max(indexArg.name().length(), maxCol);
                hasOptions = true;
            }
            if (f.isAnnotationPresent(NamedArg.class)) {
                final NamedArg namedArg = f.getAnnotation(NamedArg.class);
                maxCol = Math.max(namedArg.named().length(), maxCol);
                hasOptions = true;
            }

        }

        if (hasOptions) {

            sb.append("\nOptions :\n\n");
            for (Field f : clazz.getDeclaredFields()) {
                if (f.isAnnotationPresent(NamedArg.class)) {
                    final NamedArg namedArg = f.getAnnotation(NamedArg.class);
                    final String named = "[" + namedArg.named() + (namedArg.hasValue() ? ":" : "") + "]";
                    final int diff = Math.max(maxCol, named.length()) - named.length();
                    for (int i = 0; i < diff + 2; i++) {
                        sb.append(" ");
                    }
                    sb.append(named).append(" : ");
                    sb.append(namedArg.description());
                    sb.append("\n");

                    int len = diff + 2 + named.length() + 3;
                    if (!isBlank(namedArg.description2())) {

                        for (String split : split(namedArg.description2(), "\n")) {
                            for (int j = 0; j < len; j++) {
                                sb.append(" ");
                            }
                            sb.append(split).append("\n");
                        }

                    }

                }
            }

            for (Field f : clazz.getDeclaredFields()) {
                if (f.isAnnotationPresent(IndexArg.class)) {
                    final IndexArg indexArg = f.getAnnotation(IndexArg.class);
                    final int diff = Math.max(maxCol, indexArg.name().length()) - indexArg.name().length();
                    for (int i = 0; i < diff + 2; i++) {
                        sb.append(" ");
                    }
                    sb.append(indexArg.name()).append(" : ");
                    sb.append(indexArg.summary());
                    sb.append("\n");

                    int len = diff + 2 + indexArg.name().length() + 3;
                    if (!isBlank(indexArg.description())) {

                        for (String split : split(indexArg.description(), "\n")) {
                            for (int j = 0; j < len; j++) {
                                sb.append(" ");
                            }
                            sb.append(split).append("\n");
                        }

                    }

                }
            }

        }


        if (cmd.eg() != null
                && cmd.eg().length > 0) {
            sb.append("\nExample : \n\n");
            for (String eg : cmd.eg()) {
                sb.append("     ").append(eg).append("\n");
            }
        }

        return sb.toString();
    }

    /**
     * ����������˵�
     *
     * @return
     */
    private String mainHelp() {

        final TableView tableView = new TableView(new ColumnDefine[]{
                new ColumnDefine(4, false, RIGHT),
                new ColumnDefine(RIGHT),
                new ColumnDefine(1, false, RIGHT),
                new ColumnDefine(LEFT)
        });

        final Map<String, Class<?>> commandMap = Commands.getInstance().listCommands();
        final List<Class<?>> classes = new ArrayList<Class<?>>(commandMap.values());
        Collections.sort(classes, new Comparator<Class<?>>() {

            @Override
            public int compare(Class<?> o1, Class<?> o2) {
                return new Integer(o1.getAnnotation(Cmd.class).sort()).compareTo(new Integer(o2.getAnnotation(Cmd.class).sort()));
            }

        });
        for (Class<?> clazz : classes) {

            if (clazz.isAnnotationPresent(Cmd.class)) {

                final Cmd cmd = clazz.getAnnotation(Cmd.class);
                tableView.addRow(
                        "    ",
                        cmd.named(),
                        " : ",
                        cmd.desc());

            }

        }

        tableView.setDrawBorder(false);
        tableView.setPadding(0);

        return format("\nGreys usage:\n\n%s", tableView.draw());

    }

}
