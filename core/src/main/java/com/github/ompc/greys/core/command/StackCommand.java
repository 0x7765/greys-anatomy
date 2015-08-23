package com.github.ompc.greys.core.command;

import com.github.ompc.greys.core.GlobalOptions;
import com.github.ompc.greys.core.advisor.AdviceListener;
import com.github.ompc.greys.core.advisor.ReflectAdviceListenerAdapter;
import com.github.ompc.greys.core.command.annotation.Cmd;
import com.github.ompc.greys.core.command.annotation.IndexArg;
import com.github.ompc.greys.core.command.annotation.NamedArg;
import com.github.ompc.greys.core.util.Matcher;
import com.github.ompc.greys.core.exception.ExpressException;
import com.github.ompc.greys.core.server.Session;
import com.github.ompc.greys.core.util.Advice;
import com.github.ompc.greys.core.util.GaMethod;

import java.lang.instrument.Instrumentation;
import java.util.concurrent.atomic.AtomicInteger;

import static com.github.ompc.greys.core.util.Advice.newForAfterRetuning;
import static com.github.ompc.greys.core.util.Advice.newForAfterThrowing;
import static com.github.ompc.greys.core.util.Express.ExpressFactory.newExpress;
import static com.github.ompc.greys.core.util.GaStringUtils.getStack;
import static org.apache.commons.lang3.StringUtils.isBlank;

/**
 * Jstack命令<br/>
 * 负责输出当前方法执行上下文
 *
 * @author vlinux
 */
@Cmd(name = "stack", sort = 6, summary = "The call stack output buried point method callback each thread.",
        eg = {
            "stack -E org\\.apache\\.commons\\.lang\\.StringUtils isBlank",
            "stack org.apache.commons.lang.StringUtils isBlank",
            "stack *StringUtils isBlank",
            "stack *StringUtils isBlank params[0].length==1"
        })
public class StackCommand implements Command {

    @IndexArg(index = 0, name = "class-pattern", summary = "pattern matching of classpath.classname")
    private String classPattern;

    @IndexArg(index = 1, name = "method-pattern", summary = "pattern matching of method name")
    private String methodPattern;

    @IndexArg(index = 2, name = "condition-express", isRequired = false,
            summary = "condition express, write by groovy",
            description = ""
            + "For example\n"
            + "    TRUE  : true\n"
            + "    FALSE : false\n"
            + "    TRUE  : params.length>=0"
            + "The structure of 'advice'\n"
            + "          target : the object entity\n"
            + "           clazz : the object's class\n"
            + "          method : the constructor or method\n"
            + "    params[0..n] : the parameters of methods\n"
            + "       returnObj : the return object of methods\n"
            + "        throwExp : the throw exception of methods\n"
            + "        isReturn : the method finish by return\n"
            + "         isThrow : the method finish by throw an exception\n"
    )
    private String conditionExpress;

    @NamedArg(name = "S", summary = "including sub class")
    private boolean isIncludeSub = GlobalOptions.isIncludeSubClass;

    @NamedArg(name = "E", summary = "enable the regex pattern matching")
    private boolean isRegEx = false;

    @NamedArg(name = "n", hasValue = true, summary = "number of limit")
    private Integer numberOfLimit;

    @Override
    public Action getAction() {

        final Matcher classNameMatcher = isRegEx
                ? new Matcher.RegexMatcher(classPattern)
                : new Matcher.WildcardMatcher(classPattern);

        final Matcher methodNameMatcher = isRegEx
                ? new Matcher.RegexMatcher(methodPattern)
                : new Matcher.WildcardMatcher(methodPattern);

        return new GetEnhancerAction() {

            @Override
            public GetEnhancer action(Session session, Instrumentation inst, final Sender sender) throws Throwable {
                return new GetEnhancer() {

                    private final AtomicInteger times = new AtomicInteger();

                    @Override
                    public Matcher getClassNameMatcher() {
                        return classNameMatcher;
                    }

                    @Override
                    public Matcher getMethodNameMatcher() {
                        return methodNameMatcher;
                    }

                    @Override
                    public boolean isIncludeSub() {
                        return isIncludeSub;
                    }

                    @Override
                    public AdviceListener getAdviceListener() {
                        return new ReflectAdviceListenerAdapter() {

                            private final ThreadLocal<String> stackThreadLocal = new ThreadLocal<String>();

                            @Override
                            public void before(
                                    ClassLoader loader,
                                    Class<?> clazz,
                                    GaMethod method,
                                    Object target,
                                    Object[] args) throws Throwable {
                                stackThreadLocal.set(getStack());
                            }

                            @Override
                            public void afterThrowing(
                                    ClassLoader loader,
                                    Class<?> clazz,
                                    GaMethod method,
                                    Object target,
                                    Object[] args,
                                    Throwable throwable) throws Throwable {
                                final Advice advice = newForAfterThrowing(loader, clazz, method, target, args, throwable);
                                finishing(advice);
                            }

                            @Override
                            public void afterReturning(
                                    ClassLoader loader,
                                    Class<?> clazz,
                                    GaMethod method,
                                    Object target,
                                    Object[] args,
                                    Object returnObject) throws Throwable {
                                final Advice advice = newForAfterRetuning(loader, clazz, method, target, args, returnObject);
                                finishing(advice);
                            }

                            private boolean isPrintIfNecessary(Advice advice) {
                                try {
                                    return isBlank(conditionExpress)
                                            || newExpress(advice).is(conditionExpress);
                                } catch (ExpressException e) {
                                    return false;
                                }
                            }

                            private boolean isLimited(int currentTimes) {
                                return null != numberOfLimit
                                        && currentTimes >= numberOfLimit;
                            }

                            private void finishing(final Advice advice) {
                                if (isPrintIfNecessary(advice)) {
                                    final boolean isF = isLimited(times.incrementAndGet());
                                    sender.send(isF, stackThreadLocal.get() + "\n");
                                }
                            }

                        };
                    }
                };
            }

        };
    }

}
