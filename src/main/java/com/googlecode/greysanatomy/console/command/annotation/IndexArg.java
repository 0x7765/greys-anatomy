package com.googlecode.greysanatomy.console.command.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * ����ָ��λ�ò���
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface IndexArg {

    /**
     * �����������е�λ��
     *
     * @return �����������е�λ��
     */
    public int index();

    /**
     * ��������
     *
     * @return ��������
     */
    public String name();

    /**
     * ����ע��
     *
     * @return ����ע��
     */
    public String description() default "";

    /**
     * ����ϸ�Ĳ���ע��
     *
     * @return ����ϸ�Ĳ���ע��
     */
    public String description2() default "";

//    /**
//     * ����У��
//     *
//     * @return ����У��
//     */
//    public ArgVerifier[] verify() default {};

    /**
     * �Ƿ����
     *
     * @return �Ƿ����
     */
    public boolean isRequired() default true;

}
