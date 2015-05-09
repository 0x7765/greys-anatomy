package com.googlecode.greysanatomy.command.view;

import java.util.ArrayList;
import java.util.List;

import static com.googlecode.greysanatomy.util.GaStringUtils.repeat;

/**
 * ���������ؼ�
 * Created by vlinux on 15/5/8.
 */
public class LadderView implements View {

    // �ָ���
    private static final String LADDER_CHAR = "`-->";

    // ������
    private static final String STEP_CHAR = " ";

    // ��������
    private static final int INDENT_STEP = 2;

    private final List<String> items = new ArrayList<String>();


    @Override
    public String draw() {
        final StringBuilder ladderSB = new StringBuilder();
        int deep = 0;
        for (String item : items) {

            // ��һ����Ŀ����Ҫ�ָ���
            if (deep == 0) {
                ladderSB
                        .append(item)
                        .append("\n");
            }

            // ��������Ҫ��ӷָ���
            else {
                ladderSB
                        .append(repeat(STEP_CHAR, deep * INDENT_STEP))
                        .append(LADDER_CHAR)
                        .append(item)
                        .append("\n");
            }

            deep++;

        }
        return ladderSB.toString();
    }

    /**
     * ���һ����Ŀ
     *
     * @param item ��Ŀ
     * @return this
     */
    public LadderView addItem(String item) {
        items.add(item);
        return this;
    }

}
