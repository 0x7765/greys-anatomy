package com.github.ompc.greys.advisor;

import java.lang.reflect.Method;

/**
 * 间谍类<br/>
 * 藏匿在各个ClassLoader中
 * Created by vlinux on 15/8/23.
 */
public class Spy {

    public static ClassLoader CLASSLOADER;
    public static Method ON_BEFORE_METHOD;
    public static Method ON_RETURN_METHOD;
    public static Method ON_THROWS_METHOD;
    public static Method BEFORE_INVOKING_METHOD;
    public static Method AFTER_INVOKING_METHOD;

    public static void set(
            ClassLoader classLoader,
            Method onBeforeMethod,
            Method onReturnMethod,
            Method onThrowsMethod,
            Method beforeInvokingMethod,
            Method afterInvokingMethod) {
        CLASSLOADER = classLoader;
        ON_BEFORE_METHOD = onBeforeMethod;
        ON_RETURN_METHOD = onReturnMethod;
        ON_THROWS_METHOD = onThrowsMethod;
        BEFORE_INVOKING_METHOD = beforeInvokingMethod;
        AFTER_INVOKING_METHOD = afterInvokingMethod;
    }

}
