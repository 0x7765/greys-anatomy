package com.googlecode.greysanatomy.command;

import com.googlecode.greysanatomy.command.annotation.Cmd;
import com.googlecode.greysanatomy.command.annotation.IndexArg;
import com.googlecode.greysanatomy.command.annotation.NamedArg;
import com.googlecode.greysanatomy.server.GaServer;
import com.googlecode.greysanatomy.util.GaDetailUtils;
import com.googlecode.greysanatomy.util.GaStringUtils;
import com.googlecode.greysanatomy.util.PatternMatchingUtils;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;

import static java.lang.String.format;

/**
 * չʾ������Ϣ
 *
 * @author vlinux
 */
@Cmd(named = "sm", sort = 1, desc = "Search all have been class method JVM loading.",
        eg = {
                "sm -Ed org\\.apache\\.commons\\.lang\\.StringUtils .*",
                "sm org.apache.commons.????.StringUtils *",
                "sm -d org.apache.commons.lang.StringUtils",
                "sm *String????s *"
        })
public class SearchMethodCommand extends Command {

    @IndexArg(index = 0, name = "class-pattern", description = "pattern matching of classpath.classname")
    private String classPattern;

    @IndexArg(index = 1, name = "method-pattern", isRequired = false, description = "pattern matching of method name")
    private String methodPattern;

    @NamedArg(named = "d", description = "show the detail of method")
    private boolean isDetail = false;

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
            public void action(final GaServer gaServer, Info info, Sender sender) throws Throwable {

                // auto fix default methodPattern
                if (GaStringUtils.isBlank(methodPattern)) {
                    methodPattern = isRegEx()
                            ? ".*"
                            : "*";
                }

                final Set<String> uniqueLine = new HashSet<String>();
                final StringBuilder message = new StringBuilder();
                int clzCnt = 0;
                int mthCnt = 0;
                for (Class<?> clazz : info.getInst().getAllLoadedClasses()) {

//                    if (!clazz.getName().matches(classPattern)) {
//                        continue;
//                    }

                    if (!PatternMatchingUtils.matching(clazz.getName(), classPattern, isRegEx())) {
                        continue;
                    }

                    boolean hasMethod = false;
                    for (Method method : clazz.getDeclaredMethods()) {

                        if (/*method.getName().matches(methodPattern)*/
                                PatternMatchingUtils.matching(method.getName(), methodPattern, isRegEx())) {
                            if (isDetail) {
                                message.append(GaDetailUtils.detail(method)).append("\n");
                            } else {
                                /*
                                 * �����ظ���
								 */
                                final String line = format("%s->%s\n", clazz.getName(), method.getName());
                                if (uniqueLine.contains(line)) {
                                    continue;
                                }
                                message.append(line);
                                uniqueLine.add(line);
                            }

                            mthCnt++;
                            hasMethod = true;
                        }

                    }//for

                    if (hasMethod) {
                        clzCnt++;
                    }

                }//for

                message.append(GaStringUtils.LINE);
                message.append(format("done. method result: matching-class=%s; matching-method=%s\n", clzCnt, mthCnt));

                sender.send(true, message.toString());
            }

        };
    }

}
