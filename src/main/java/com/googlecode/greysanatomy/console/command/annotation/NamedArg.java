package com.googlecode.greysanatomy.console.command.annotation;

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
    public String named();

    /**
     * ����ע��
     *
     * @return ����ע��
     */
    public String description() default "";

    /**
     * ����ע��2
     *
     * @return ����ע��2
     */
    public String description2() default "";

    /**
     * �Ƿ���ֵ
     *
     * @return �Ƿ���ֵ
     */
    public boolean hasValue() default false;

//    /**
//     * ����У��
//     *
//     * @return ����У��
//     */
//    public ArgVerifier[] verify() default {};

}
