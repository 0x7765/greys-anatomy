package com.googlecode.greysanatomy.command.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * ����ָ�
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Cmd {

    /**
     * ָ�����������<br/>
     *
     * @return �������������
     */
    String named();

    /**
     * ָ������Ľ���
     *
     * @return ��������Ľ���
     */
    String desc();

    /**
     * ����
     *
     * @return �������������
     */
    String[] eg() default {};

    /**
     * ����,��help������
     *
     * @return ����������Ŀ¼�е�����
     */
    int sort() default 0;

}
