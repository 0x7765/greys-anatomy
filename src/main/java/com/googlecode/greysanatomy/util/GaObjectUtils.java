package com.googlecode.greysanatomy.util;

import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.lang.reflect.Field;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Date;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Scanner;

import static java.lang.String.format;

/**
 * Object�Ĺ�����
 * Created by vlinux on 14/12/8.
 */
public class GaObjectUtils {

    private final static String TAB = "    ";

    public static String toString(Object obj, int deep, int expand) {

        final StringBuilder buf = new StringBuilder();
        if (null == obj) {
            buf.append(obj);
        } else {

            final Class<?> clazz = obj.getClass();
            final String className = clazz.getSimpleName();

            // 8�ֻ�������,ֱ�����@����[ֵ]
            if (Integer.class.isInstance(obj)
                    || Long.class.isInstance(obj)
                    || Float.class.isInstance(obj)
                    || Double.class.isInstance(obj)
                    || Character.class.isInstance(obj)
                    || Short.class.isInstance(obj)
                    || Byte.class.isInstance(obj)
                    || Boolean.class.isInstance(obj)) {
                buf.append(format("@%s[%s]", className, obj));
            }

            // �ַ������͵�������
            else if (String.class.isInstance(obj)) {
                final StringBuilder bufOfString = new StringBuilder();
                for (Character c : ((String) obj).toCharArray()) {
                    switch (c) {
                        case '\n':
                            bufOfString.append("\\n");
                            break;
                        case '\r':
                            bufOfString.append("\\r");
                            break;
                        default:
                            bufOfString.append(c);
                    }//switch
                }//for
                buf.append(format("@%s[%s]", className, bufOfString));
            }

            // ���������
            else if (Collection.class.isInstance(obj)) {

                final Collection<Object> collection = (Collection<Object>) obj;

                // �Ǹ��ڵ��ռ���ֻչʾժҪ��Ϣ
                if (!isExpand(deep, expand)
                        || collection.isEmpty()) {

                    buf.append(format("@%s[isEmpty=%s;size=%d]",
                            className,
                            collection.isEmpty(),
                            collection.size()));

                }

                // չ��չʾ
                else {

                    final StringBuilder bufOfCollection = new StringBuilder();
                    bufOfCollection.append(format("@%s[", className));
                    for (Object e : collection) {
                        bufOfCollection.append("\n").append(toString(e, deep + 1, expand)).append(",\n");
                    }
                    bufOfCollection.append("]");
                    buf.append(bufOfCollection);
                }

            }


            // Map�����
            else if (Map.class.isInstance(obj)) {

                final Map<Object, Object> map = (Map<Object, Object>) obj;

                // �Ǹ��ڵ��ռ���ֻչʾժҪ��Ϣ
                if (!isExpand(deep, expand)
                        || map.isEmpty()) {

                    buf.append(format("@%s[isEmpty=%s;size=%d]",
                            className,
                            map.isEmpty(),
                            map.size()));

                } else {

                    final StringBuilder bufOfMap = new StringBuilder();
                    bufOfMap.append(format("@%s[", className));
                    for (Entry<Object, Object> entry : map.entrySet()) {
                        bufOfMap.append("\n").append(toString(entry.getKey(), deep + 1, expand))
                                .append(":\n")
                                .append(toString(entry.getValue(), deep + 1, expand))
                                .append(",\n");
                    }
                    bufOfMap.append("]");
                    buf.append(bufOfMap);
                }

            }


            // ���������
            else if (obj.getClass().isArray()) {


                final String typeName = obj.getClass().getSimpleName();

                // int[]
                if (typeName.equals("int[]")) {

                    final int[] arrays = (int[]) obj;
                    // �Ǹ��ڵ��ռ���ֻչʾժҪ��Ϣ
                    if (!isExpand(deep, expand)
                            || arrays.length == 0) {

                        buf.append(format("@%s[isEmpty=%s;size=%d]",
                                typeName,
                                arrays.length == 0,
                                arrays.length));

                    }

                    // չ��չʾ
                    else {

                        final StringBuilder bufOfArray = new StringBuilder();
                        bufOfArray.append(format("@%s[", className));
                        for (Object e : arrays) {
                            bufOfArray.append("\n").append(toString(e, deep + 1, expand)).append(",");
                        }
                        bufOfArray.append("\n]");
                        buf.append(bufOfArray);
                    }

                }

                // long[]
                else if (typeName.equals("long[]")) {

                    final long[] arrays = (long[]) obj;
                    // �Ǹ��ڵ��ռ���ֻչʾժҪ��Ϣ
                    if (!isExpand(deep, expand)
                            || arrays.length == 0) {

                        buf.append(format("@%s[isEmpty=%s;size=%d]",
                                typeName,
                                arrays.length == 0,
                                arrays.length));

                    }

                    // չ��չʾ
                    else {

                        final StringBuilder bufOfArray = new StringBuilder();
                        bufOfArray.append(format("@%s[", className));
                        for (Object e : arrays) {
                            bufOfArray.append("\n").append(toString(e, deep + 1, expand)).append(",");
                        }
                        bufOfArray.append("\n]");
                        buf.append(bufOfArray);
                    }

                }

                // short[]
                else if (typeName.equals("short[]")) {

                    final short[] arrays = (short[]) obj;
                    // �Ǹ��ڵ��ռ���ֻչʾժҪ��Ϣ
                    if (!isExpand(deep, expand)
                            || arrays.length == 0) {

                        buf.append(format("@%s[isEmpty=%s;size=%d]",
                                typeName,
                                arrays.length == 0,
                                arrays.length));

                    }

                    // չ��չʾ
                    else {

                        final StringBuilder bufOfArray = new StringBuilder();
                        bufOfArray.append(format("@%s[", className));
                        for (Object e : arrays) {
                            bufOfArray.append("\n").append(toString(e, deep + 1, expand)).append(",");
                        }
                        bufOfArray.append("\n]");
                        buf.append(bufOfArray);
                    }

                }

                // float[]
                else if (typeName.equals("float[]")) {

                    final float[] arrays = (float[]) obj;
                    // �Ǹ��ڵ��ռ���ֻչʾժҪ��Ϣ
                    if (!isExpand(deep, expand)
                            || arrays.length == 0) {

                        buf.append(format("@%s[isEmpty=%s;size=%d]",
                                typeName,
                                arrays.length == 0,
                                arrays.length));

                    }

                    // չ��չʾ
                    else {

                        final StringBuilder bufOfArray = new StringBuilder();
                        bufOfArray.append(format("@%s[", className));
                        for (Object e : arrays) {
                            bufOfArray.append("\n").append(toString(e, deep + 1, expand)).append(",");
                        }
                        bufOfArray.append("\n]");
                        buf.append(bufOfArray);
                    }

                }

                // double[]
                else if (typeName.equals("double[]")) {

                    final double[] arrays = (double[]) obj;
                    // �Ǹ��ڵ��ռ���ֻչʾժҪ��Ϣ
                    if (!isExpand(deep, expand)
                            || arrays.length == 0) {

                        buf.append(format("@%s[isEmpty=%s;size=%d]",
                                typeName,
                                arrays.length == 0,
                                arrays.length));

                    }

                    // չ��չʾ
                    else {

                        final StringBuilder bufOfArray = new StringBuilder();
                        bufOfArray.append(format("@%s[", className));
                        for (Object e : arrays) {
                            bufOfArray.append("\n").append(toString(e, deep + 1, expand)).append(",");
                        }
                        bufOfArray.append("\n]");
                        buf.append(bufOfArray);
                    }

                }

                // boolean[]
                else if (typeName.equals("boolean[]")) {

                    final boolean[] arrays = (boolean[]) obj;
                    // �Ǹ��ڵ��ռ���ֻչʾժҪ��Ϣ
                    if (!isExpand(deep, expand)
                            || arrays.length == 0) {

                        buf.append(format("@%s[isEmpty=%s;size=%d]",
                                typeName,
                                arrays.length == 0,
                                arrays.length));

                    }

                    // չ��չʾ
                    else {

                        final StringBuilder bufOfArray = new StringBuilder();
                        bufOfArray.append(format("@%s[", className));
                        for (Object e : arrays) {
                            bufOfArray.append("\n").append(toString(e, deep + 1, expand)).append(",");
                        }
                        bufOfArray.append("\n]");
                        buf.append(bufOfArray);
                    }

                }

                // char[]
                else if (typeName.equals("char[]")) {

                    final char[] arrays = (char[]) obj;
                    // �Ǹ��ڵ��ռ���ֻչʾժҪ��Ϣ
                    if (!isExpand(deep, expand)
                            || arrays.length == 0) {

                        buf.append(format("@%s[isEmpty=%s;size=%d]",
                                typeName,
                                arrays.length == 0,
                                arrays.length));

                    }

                    // չ��չʾ
                    else {

                        final StringBuilder bufOfArray = new StringBuilder();
                        bufOfArray.append(format("@%s[", className));
                        for (Object e : arrays) {
                            bufOfArray.append("\n").append(toString(e, deep + 1, expand)).append(",");
                        }
                        bufOfArray.append("\n]");
                        buf.append(bufOfArray);
                    }

                }

                // byte[]
                else if (typeName.equals("byte[]")) {

                    final byte[] arrays = (byte[]) obj;
                    // �Ǹ��ڵ��ռ���ֻչʾժҪ��Ϣ
                    if (!isExpand(deep, expand)
                            || arrays.length == 0) {

                        buf.append(format("@%s[isEmpty=%s;size=%d]",
                                typeName,
                                arrays.length == 0,
                                arrays.length));

                    }

                    // չ��չʾ
                    else {

                        final StringBuilder bufOfArray = new StringBuilder();
                        bufOfArray.append(format("@%s[", className));
                        for (Object e : arrays) {
                            bufOfArray.append("\n").append(toString(e, deep + 1, expand)).append(",");
                        }
                        bufOfArray.append("\n]");
                        buf.append(bufOfArray);
                    }

                }

                // Object[]
                else {
                    final Object[] arrays = (Object[]) obj;
                    // �Ǹ��ڵ��ռ���ֻչʾժҪ��Ϣ
                    if (!isExpand(deep, expand)
                            || arrays.length == 0) {

                        buf.append(format("@%s[isEmpty=%s;size=%d]",
                                typeName,
                                arrays.length == 0,
                                arrays.length));

                    }

                    // չ��չʾ
                    else {

                        final StringBuilder bufOfArray = new StringBuilder();
                        bufOfArray.append(format("@%s[", className));
                        for (Object e : arrays) {
                            bufOfArray.append("\n").append(toString(e, deep + 1, expand)).append(",");
                        }
                        bufOfArray.append("\n]");
                        buf.append(bufOfArray);
                    }
                }

            }


            // Throwable���
            else if (Throwable.class.isInstance(obj)) {

                if (!isExpand(deep, expand)) {
                    buf.append(format("@%s[%s]", className, obj));
                } else {

                    final Throwable throwable = (Throwable) obj;
                    final StringWriter sw = new StringWriter();
                    final PrintWriter pw = new PrintWriter(sw);
                    throwable.printStackTrace(pw);

//                    buf.append(format("@%s[\n",className));
                    buf.append(sw.toString());
//                    buf.append("]");

                }

            }

            // Date���
            else if( Date.class.isInstance(obj) ) {
                buf.append(format("@%s[%s]", className, new SimpleDateFormat("yyyy-MM-dd HH:mm:ss,SSS").format(obj)));
            }

            // ��ͨObject���
            else {

                if (!isExpand(deep, expand)) {
                    buf.append(format("@%s[%s]", className, obj));
                } else {

                    final StringBuilder bufOfObject = new StringBuilder();
                    bufOfObject.append(format("@%s[", className));
                    final Field[] fields = obj.getClass().getDeclaredFields();
                    if (null != fields) {
                        for (Field field : fields) {

                            field.setAccessible(true);

                            try {

                                final Object value = field.get(obj);

                                bufOfObject.append("\n").append(TAB).append(field.getName())
                                        .append("=")
                                        .append(toString(value, deep + 1, expand).trim())
                                        .append(",");

                            } catch (Throwable t) {
                                //
                            }

                        }//for
                        bufOfObject.append("\n");
                    }//if
                    bufOfObject.append("]");
                    buf.append(bufOfObject);

                }

            }

        }

        // TABƫ��
        final StringBuilder tabBuf = new StringBuilder();
        Scanner scanner = null;
        try {
            scanner = new Scanner(new StringReader(buf.toString()));
            while (scanner.hasNextLine()) {
                final String line = scanner.nextLine();
                if (!isRoot(deep)) {
                    tabBuf.append(TAB);
                }
                tabBuf.append(line).append("\n");
            }
        } finally {
            if (null != scanner) {
                scanner.close();
            }
        }
        if (tabBuf.length() > 0) {
            tabBuf.deleteCharAt(tabBuf.length() - 1);
        }

        return tabBuf.toString();

    }

    /**
     * �Ƿ���ڵ�
     *
     * @param deep ���
     * @return true:���ڵ� / false:�Ǹ��ڵ�
     */
    private static boolean isRoot(int deep) {
        return deep == 0;
    }


    /**
     * �Ƿ�չ����ǰ��ȵĽڵ�
     *
     * @param deep   ��ǰ�ڵ�����
     * @param expand չ������
     * @return true:��ǰ�ڵ���Ҫչ�� / false:��ǰ�ڵ㲻��Ҫչ��
     */
    private static boolean isExpand(int deep, int expand) {
        return deep < expand;
    }

//    public static void main(String... args) {
//
//        System.out.println( toString(new Exception(),0,3) );
//        System.out.println(int[].class.getSimpleName());
//
//    }

}
