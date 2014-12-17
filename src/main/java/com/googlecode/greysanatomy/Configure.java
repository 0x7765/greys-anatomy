package com.googlecode.greysanatomy;

import java.lang.reflect.Field;
import java.net.InetAddress;
import java.net.UnknownHostException;

import static com.googlecode.greysanatomy.util.GaReflectUtils.*;
import static com.googlecode.greysanatomy.util.GaStringUtils.*;

/**
 * ������
 *
 * @author vlinux
 */
public class Configure {

    private String targetIp;                //Ŀ������IP
    private int targetPort;                 //Ŀ����̺�
    private int javaPid;                    //�Է�java���̺�
    private long connectTimeout = 6000;     //���ӳ�ʱʱ��(ms)
    private boolean multi;                  //���û�ģʽ
    private String consolePrompt = "ga?>";  //����̨��ʾ��

    /**
     * ��Configer����ת��Ϊ�ַ���
     */
    public String toString() {
        final StringBuilder strSB = new StringBuilder();
        for (Field field : getFields(Configure.class)) {
            try {
                strSB.append(field.getName()).append("=").append(encode(newString(getFieldValueByField(this, field)))).append(";");
            } catch (Throwable t) {
                //
            }
        }//for
        return strSB.toString();
    }

    /**
     * ��toString������ת��ΪConfiger����
     *
     * @param toString
     * @return
     */
    public static Configure toConfiger(String toString) {
        final Configure configure = new Configure();
        final String[] pvs = split(toString, ";");
        for (String pv : pvs) {
            try {
                final String[] strs = split(pv, "=");
                final String p = strs[0];
                final String v = decode(strs[1]);
                final Field field = getField(Configure.class, p);
                set(field, valueOf(field.getType(), v), configure);
            } catch (Throwable t) {
                //
            }
        }
        return configure;
    }

    public String getTargetIp() {
        return targetIp;
    }

    public void setTargetIp(String targetIp) {
        this.targetIp = targetIp;
    }

    public int getTargetPort() {
        return targetPort;
    }

    public void setTargetPort(int targetPort) {
        this.targetPort = targetPort;
    }

    public int getJavaPid() {
        return javaPid;
    }

    public void setJavaPid(int javaPid) {
        this.javaPid = javaPid;
    }

    public long getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(long connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public boolean isMulti() {
        return multi;
    }

    public void setMulti(boolean multi) {
        this.multi = multi;
    }

    public String getConsolePrompt() {
        return consolePrompt;
    }

    public void setConsolePrompt(String consolePrompt) {
        this.consolePrompt = consolePrompt;
    }

    public static void main(String... args) throws UnknownHostException {
        System.out.println(InetAddress.getLocalHost().toString());
    }

}
