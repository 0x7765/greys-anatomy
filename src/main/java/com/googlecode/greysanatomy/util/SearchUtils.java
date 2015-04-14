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
     * @param inst         inst
     * @param classPattern classPattern
     * @param isRegEx
     * @return
     */
    public static Set<Class<?>> searchClassByClassPatternMatching(Instrumentation inst, String classPattern, boolean isRegEx) {
        final Set<Class<?>> matches = new HashSet<Class<?>>();
        for (Class<?> clazz : inst.getAllLoadedClasses()) {

            if( PatternMatchingUtils.matching(clazz.getName(), classPattern, isRegEx) ) {
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
