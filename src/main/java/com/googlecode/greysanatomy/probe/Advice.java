package com.googlecode.greysanatomy.probe;

/**
 * ֪ͨ��
 *
 * @author vlinux
 */
public class Advice {

    /**
     * ̽��Ŀ��
     *
     * @author vlinux
     */
    public static class Target {

        private final String targetClassName;
        private final String targetBehaviorName;
        private final Object targetThis;
        private final Class targetClass;
        private final Class[] parameterTypes;

        public Target(String targetClassName, String targetBehaviorName, Object targetThis, Class targetClass, Class[] parameterTypes) {
            this.targetClassName = targetClassName;
            this.targetBehaviorName = targetBehaviorName;
            this.targetThis = targetThis;
            this.targetClass = targetClass;
            this.parameterTypes = parameterTypes;
        }

        /**
         * ��ȡ̽��Ŀ��������
         * @return ��̽��Ŀ��������
         */
        public Class getTargetClass() {
            return targetClass;
        }

        /**
         * ��ȡ̽��Ŀ�����������
         * @return ��̽��Ŀ�����������
         */
        public Class[] getParameterTypes() {
            return parameterTypes;
        }

        /**
         * ��ȡ̽��Ŀ��������
         *
         * @return ��̽���Ŀ��������
         */
        public String getTargetClassName() {
            return targetClassName;
        }

        /**
         * ��ȡ̽��Ŀ����Ϊ(method/constructor)����
         *
         * @return ��̽�����Ϊ����
         */
        public String getTargetBehaviorName() {
            return targetBehaviorName;
        }

        /**
         * ��ȡ̽��Ŀ��ʵ��
         *
         * @return ��̽��Ŀ��ʵ��
         */
        public Object getTargetThis() {
            return targetThis;
        }

    }

    private final Target target;        // ̽��Ŀ��
    private final Object[] parameters;    // ���ò���
    private final boolean isFinished;    // �Ƿ�doFinish����

    private Object returnObj;            // ����ֵ�����Ŀ�귽�������쳣����ʽ���������ֵΪnull
    private Throwable throwException;    // �׳��쳣�����Ŀ�귽����������ʽ���������ֵΪnull

    public Advice(Target target, Object[] parameters, boolean isFinished) {
        this.target = target;
        this.parameters = parameters;
        this.isFinished = isFinished;
    }

    /**
     * �Ƿ����׳��쳣����
     *
     * @return true:�����쳣��ʽ����/false:�Է����쳣��ʽ����������δ����
     */
    public boolean isThrowException() {
        return isFinished() && null != throwException;
    }

    /**
     * �Ƿ����������ؽ���
     *
     * @return true:������������ʽ����/false:�Է�����������ʽ����������δ����
     */
    public boolean isReturn() {
        return isFinished() && !isThrowException();
    }

    /**
     * �Ƿ��Ѿ�����
     *
     * @return true:�Ѿ�����/false:��δ����
     */
    public boolean isFinished() {
        return isFinished;
    }

    public Target getTarget() {
        return target;
    }

    public Object getReturnObj() {
        return returnObj;
    }

    public void setReturnObj(Object returnObj) {
        this.returnObj = returnObj;
    }

    public Throwable getThrowException() {
        return throwException;
    }

    public void setThrowException(Throwable throwException) {
        this.throwException = throwException;
    }

    public Object[] getParameters() {
        return parameters;
    }

    /**
     * getParameters()�����ı�����ԭ��������̫TM����
     *
     * @return �����б�
     */
    public Object[] getParams() {
        return parameters;
    }

    /**
     * getThrowException()�����ı���
     *
     * @return �쳣����
     */
    public Throwable getThrowExp() {
        return throwException;
    }

}
