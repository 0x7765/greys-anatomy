package com.googlecode.greysanatomy.command.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * ����ָ����������
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface NamedArg {

    /**
     * �����������е�λ��
     *
     * @return �����������е�λ��
     */
    String named();

    /**
     * ����ע��
     *
     * @return ����ע��
     */
    String description() default "";

    /**
     * ����ע��2
     *
     * @return ����ע��2
     */
    String description2() default "";

    /**
     * �Ƿ���ֵ
     *
     * @return �Ƿ���ֵ
     */
    boolean hasValue() default false;

//    /**
//     * ����У��
//     *
//     * @return ����У��
//     */
//    public ArgVerifier[] verify() default {};

}
