package com.googlecode.greysanatomy.util;

import java.lang.instrument.Instrumentation;
import java.util.HashSet;
import java.util.Set;

/**
 * �����ù�����
 *
 * @author chengtongda
 */
public class SearchUtils {

    /**
     * ��������������ʽ��
     *
     * @param inst
     * @return
     */
    public static Set<Class<?>> searchClassByClassRegex(Instrumentation inst, String classRegex) {
        final Set<Class<?>> matches = new HashSet<Class<?>>();
        for (Class<?> clazz : inst.getAllLoadedClasses()) {
            if (clazz.getName().matches(classRegex)) {
                matches.add(clazz);
            }
        }//for
        return matches;
    }

    /**
     * ���ݸ���������
     *
     * @param inst
     * @param supers
     * @return
     */
    public static Set<Class<?>> searchClassBySupers(Instrumentation inst, Set<Class<?>> supers) {
        final Set<Class<?>> matches = new HashSet<Class<?>>();
        for (Class<?> clazz : inst.getAllLoadedClasses()) {
            for (Class<?> superClass : supers) {
                if (superClass.isAssignableFrom(clazz)) {
                    matches.add(clazz);
                    break;
                }
            }
        }//for
        return matches;
    }

}
