package com.googlecode.greysanatomy;

import java.lang.reflect.Field;

import static com.googlecode.greysanatomy.util.GaReflectUtils.*;
import static com.googlecode.greysanatomy.util.GaStringUtils.*;

/**
 * ������
 *
 * @author vlinux
 */
public class Configure {

    private String targetIp;                // Ŀ������IP
    private int targetPort;                 // Ŀ����̺�
    private int javaPid;                    // �Է�java���̺�
    private int connectTimeout = 6000;      // ���ӳ�ʱʱ��(ms)
    private String consolePrompt = "ga?>";  // ����̨��ʾ��

    /**
     * ��Configure����ת��Ϊ�ַ���
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
     * ��toString������ת��ΪConfigure����
     *
     * @param toString
     * @return
     */
    public static Configure toConfigure(String toString) {
        final Configure configure = new Configure();
        final String[] pvs = split(toString, ";");
        for (String pv : pvs) {
            try {
                final String[] stringSplitArray = split(pv, "=");
                final String p = stringSplitArray[0];
                final String v = decode(stringSplitArray[1]);
                final Field field = getField(Configure.class, p);
                if (null != field) {
                    set(field, valueOf(field.getType(), v), configure);
                }
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

    public String getConsolePrompt() {
        return consolePrompt;
    }

    public int getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(int connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

}
