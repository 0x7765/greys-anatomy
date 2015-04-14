package com.googlecode.greysanatomy.util;

/**
 * ģʽƥ�乤����
 */
public class PatternMatchingUtils {

    /**
     * ����ģʽƥ��
     *
     * @param string
     * @param pattern
     * @param isRegEx
     * @return
     */
    public static boolean matching(String string, String pattern, boolean isRegEx) {

        if (isRegEx) {
            return string.matches(pattern);
        } else {
            return WildcardMatchingUtils.match(string, pattern);
        }

    }
}
