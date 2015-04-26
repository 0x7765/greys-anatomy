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
    int index();

    /**
     * ��������
     *
     * @return ��������
     */
    String name();

    /**
     * ����ע��
     *
     * @return ����ע��
     */
    String description() default "";

    /**
     * ����ϸ�Ĳ���ע��
     *
     * @return ����ϸ�Ĳ���ע��
     */
    String description2() default "";

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
    boolean isRequired() default true;

}
