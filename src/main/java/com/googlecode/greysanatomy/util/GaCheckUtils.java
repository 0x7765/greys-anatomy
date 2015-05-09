package com.googlecode.greysanatomy.util;

/**
 * �쳵������
 *
 * @author vlinux
 */
public class GaCheckUtils {

    /**
     * �ж�ĳ��ֵ�Ƿ���ĳƬֵ��Χ֮��
     *
     * @param <E>
     * @param e
     * @param collections
     * @return
     */
    public static <E> boolean isIn(E e, E... collections) {
        if (null == collections || collections.length == 0) {
            return false;
        }//if
        for (E ce : collections) {
            if ((null == e && null == ce)
                    || (null != e && null != ce && e.equals(ce))) {
                return true;
            }//if
        }//for
        return false;
    }

}
