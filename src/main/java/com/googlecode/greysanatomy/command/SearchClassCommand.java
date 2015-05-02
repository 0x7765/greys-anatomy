package com.googlecode.greysanatomy.command;

import com.googlecode.greysanatomy.command.annotation.Cmd;
import com.googlecode.greysanatomy.command.annotation.IndexArg;
import com.googlecode.greysanatomy.command.annotation.NamedArg;
import com.googlecode.greysanatomy.server.GaServer;
import com.googlecode.greysanatomy.util.GaDetailUtils;
import com.googlecode.greysanatomy.util.GaStringUtils;

import java.util.Set;

import static com.googlecode.greysanatomy.util.SearchUtils.searchClassByClassPatternMatching;
import static com.googlecode.greysanatomy.util.SearchUtils.searchClassBySupers;
import static java.lang.String.format;

/**
 * չʾ����Ϣ
 *
 * @author vlinux
 */
@Cmd(named = "sc", sort = 0, desc = "Search all have been loaded by the JVM class.",
        eg = {
                "sc -E org\\.apache\\.commons\\.lang\\.StringUtils",
//                "sc -s org.apache.commons.lang.StringUtils",
                "sc -d org.apache.commons.lang.StringUtils",
                "sc -Sd *StringUtils"
        })
public class SearchClassCommand extends Command {

    @IndexArg(index = 0, name = "class-pattern", description = "pattern matching of classpath.classname")
    private String classPattern;

    @NamedArg(named = "S", description = "including sub class")
    private boolean isSuper = false;

    @NamedArg(named = "d", description = "show the detail of class")
    private boolean isDetail = false;

    @NamedArg(named = "f", description = "show the fields of class")
    private boolean isField = false;

    @NamedArg(named = "E", description = "enable the regex pattern matching")
    private boolean isRegEx = false;

    /**
     * �����Ƿ�����������ʽƥ��
     *
     * @return true����������ʽ/false������
     */
    public boolean isRegEx() {
        return isRegEx;
    }

    @Override
    public Action getAction() {
        return new Action() {

            @Override
            public void action(final GaServer gaServer, final Info info, final Sender sender) throws Throwable {

                final StringBuilder message = new StringBuilder();
                final Set<Class<?>> matchedClassSet;
                if (isSuper) {

                    matchedClassSet = searchClassBySupers(
                            info.getInst(),
                            searchClassByClassPatternMatching(info.getInst(), classPattern, isRegEx()));
                } else {
                    matchedClassSet = searchClassByClassPatternMatching(info.getInst(), classPattern, isRegEx());
                }

                for (Class<?> clazz : matchedClassSet) {
                    if (isDetail) {
                        message.append(GaDetailUtils.detail(clazz, isField)).append("\n");
                    } else {
                        message.append(clazz.getName()).append("\n");
                    }
                }

                message.append(GaStringUtils.LINE);
                message.append(format("done. classes result: matching-class=%s;\n", matchedClassSet.size()));
                sender.send(true, message.toString());
            }

        };
    }

}
