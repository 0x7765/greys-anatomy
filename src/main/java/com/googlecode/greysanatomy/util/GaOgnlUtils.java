package com.googlecode.greysanatomy.util;

import ognl.DefaultMemberAccess;
import ognl.Ognl;
import ognl.OgnlContext;
import ognl.OgnlException;

import java.util.Map;

/**
 * Ga��Ognl����
 * Created by vlinux on 14/12/10.
 */
public class GaOgnlUtils {

    /**
     * ��ȡognl���ʽ��ֵ
     *
     * @param express OGNL���ʽ
     * @param object  ��ȡ�����ݶ���
     * @return ������ֵ
     * @throws OgnlException ��ȡ�쳣
     */
    public static Object getValue(String express, Object object) throws OgnlException {
        final Map<String, Object> context = Ognl.createDefaultContext(null);
        context.put(OgnlContext.MEMBER_ACCESS_CONTEXT_KEY, new DefaultMemberAccess(true, true, true));
        return Ognl.getValue(express, context, object);
    }


    /**
     * ʹ��ognl���ʽ������ƥ��
     *
     * @param conditionExpress OGNL�������ʽ
     * @param object           ��������ƥ��Ķ���
     * @return ƥ����
     * @throws OgnlException ƥ���쳣
     */
    public static boolean is(String conditionExpress, Object object) throws OgnlException {
        final Object ret =  getValue(conditionExpress, object);

        return null != ret
                && ret instanceof Boolean
                && (Boolean)ret;

    }

}
