package com.googlecode.greysanatomy.console.command.annotation;

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
    public String named();

    /**
     * ָ������Ľ���
     *
     * @return ��������Ľ���
     */
    public String desc();

    /**
     * ����
     *
     * @return �������������
     */
    public String[] eg() default {};

    /**
     * ����,��help������
     *
     * @return ����������Ŀ¼�е�����
     */
    public int sort() default 0;

}
