package com.googlecode.greysanatomy.command.view;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

import static com.googlecode.greysanatomy.util.GaStringUtils.*;
import static java.lang.Math.abs;
import static java.lang.Math.max;
import static java.lang.String.format;

/**
 * ���ؼ�
 * Created by vlinux on 15/5/7.
 */
public class TableView implements View {

    private final ColumnDefine[] columnDefineArray;

    // �Ƿ���Ⱦ�߿�
    private boolean drawBorder;

    // �����
    private int padding;

    public TableView(ColumnDefine[] columnDefineArray) {
        this.columnDefineArray = null == columnDefineArray
                ? new ColumnDefine[0]
                : columnDefineArray;
    }

    public TableView(int columnNum) {
        this.columnDefineArray = new ColumnDefine[columnNum];
        for (int index = 0; index < this.columnDefineArray.length; index++) {
            columnDefineArray[index] = new ColumnDefine();
        }
    }

    @Override
    public String draw() {
        final StringBuilder tableSB = new StringBuilder();

        // init width cache
        final int[] widthCacheArray = new int[getColumnCount()];
        for (int index = 0; index < widthCacheArray.length; index++) {
            widthCacheArray[index] = abs(columnDefineArray[index].getWidth());
        }

        final int tableHigh = getTableHigh();
        for (int rowIndex = 0; rowIndex < tableHigh; rowIndex++) {

            final boolean isLastRow = rowIndex == tableHigh - 1;

            // ��ӡ�ָ���
            if (isDrawBorder()) {
                tableSB.append(drawSeparationLine(widthCacheArray)).append("\n");
            }

            // ��һ��
            drawLine(tableSB, widthCacheArray, rowIndex);


            // ��ӡ��β�ָ���
            if (isLastRow
                    && isDrawBorder()) {
                // ��ӡ�ָ���
                tableSB.append(drawSeparationLine(widthCacheArray));
            }

        }


        return tableSB.toString();
    }


    private void drawLine(StringBuilder tableSB, int[] widthCacheArray, int rowIndex) {

        final Scanner[] scannerArray = new Scanner[getColumnCount()];
        try {
            boolean hasNext;
            do {

                hasNext = false;
                final StringBuilder segmentSB = new StringBuilder();

                for (int colIndex = 0; colIndex < getColumnCount(); colIndex++) {

                    if (null == scannerArray[colIndex]) {
                        scannerArray[colIndex] = new Scanner(
                                new StringReader(
                                        getData(rowIndex, columnDefineArray[colIndex])));
                    }

                    final String borderChar = isDrawBorder() ? "|" : EMPTY;
                    final int width = widthCacheArray[colIndex];
                    final boolean isLastColOfRow = colIndex == widthCacheArray.length - 1;
                    final Scanner scanner = scannerArray[colIndex];

                    final String data;
                    if (scanner.hasNext()) {
                        data = scanner.nextLine();
                        hasNext = true;
                    } else {
                        data = EMPTY;
                    }

                    if (width > 0) {

                        final ColumnDefine columnDefine = columnDefineArray[colIndex];
                        final String dataFormat = getDataFormat(columnDefine, width);
                        final String paddingChar = repeat(" ", padding);

                        segmentSB.append(
                                format(borderChar + paddingChar + dataFormat + paddingChar,
                                        summary(data, width)));

                    }

                    if (isLastColOfRow) {
                        segmentSB.append(borderChar).append("\n");
                    }

                }

                if (hasNext) {
                    tableSB.append(segmentSB);
                }

            } while (hasNext);
        } finally {
            for (Scanner scanner : scannerArray) {
                if( null != scanner ) {
                    scanner.close();
                }
            }
        }

    }

    private String getData(int rowIndex, ColumnDefine columnDefine) {
        return columnDefine.getHigh() <= rowIndex
                ? EMPTY
                : columnDefine.dataList.get(rowIndex);
    }

    private String getDataFormat(ColumnDefine columnDefine, int width) {
        switch (columnDefine.align) {
            case RIGHT: {
                return "%" + width + "s";
            }
            case LEFT:
            default: {
                return "%-" + width + "s";
            }
        }
    }

    /*
     * ��ȡ���߶�
     */
    private int getTableHigh() {
        int tableHigh = 0;
        for (ColumnDefine columnDefine : columnDefineArray) {
            tableHigh = max(tableHigh, columnDefine.getHigh());
        }
        return tableHigh;
    }

    /*
     * ��ӡ�ָ���
     */
    private String drawSeparationLine(int[] widthCacheArray) {
        final StringBuilder separationLineSB = new StringBuilder();

        for (int width : widthCacheArray) {
            if (width > 0) {
                separationLineSB.append("+").append(repeat("-", width + 2 * padding));
            }
        }

        separationLineSB.append("+");

        return separationLineSB.toString();
    }


    /**
     * ���������
     *
     * @param columnDataArray ��������
     */
    public TableView addRow(Object... columnDataArray) {
        if (null == columnDataArray) {
            return this;
        }

        for (int index = 0; index < columnDefineArray.length; index++) {
            final ColumnDefine columnDefine = columnDefineArray[index];
            if (index < columnDataArray.length
                    && null != columnDataArray[index]) {
                columnDefine.dataList.add(columnDataArray[index].toString());
            } else {
                columnDefine.dataList.add(EMPTY);
            }
        }

        return this;

    }


    /**
     * ���뷽��
     */
    public enum Align {
        LEFT,
        RIGHT
    }

    /**
     * �ж���
     */
    public static class ColumnDefine {

        private final int width;
        private final boolean isAutoResize;
        private final Align align;
        private final List<String> dataList = new ArrayList<String>();

        public ColumnDefine(int width, boolean isAutoResize, Align align) {
            this.width = width;
            this.isAutoResize = isAutoResize;
            this.align = align;
        }

        public ColumnDefine(Align align) {
            this(0, true, align);
        }

        public ColumnDefine() {
            this(Align.LEFT);
        }

        /**
         * ��ȡ��ǰ�еĿ��
         *
         * @return ���
         */
        public int getWidth() {

            if (!isAutoResize) {
                return width;
            }

            int maxWidth = 0;
            for (String data : dataList) {
                final Scanner scanner = new Scanner(new StringReader(data));
                try {
                    while (scanner.hasNext()) {
                        maxWidth = max(length(scanner.nextLine()), maxWidth);
                    }
                } finally {
                    scanner.close();
                }
            }

            return maxWidth;
        }

        /**
         * ��ȡ��ǰ�еĸ߶�
         *
         * @return �߶�
         */
        public int getHigh() {
            return dataList.size();
        }

    }

    /**
     * �����Ƿ񻭱߿�
     *
     * @param isDrawBorder true / false
     */
    public TableView setDrawBorder(boolean isDrawBorder) {
        this.drawBorder = isDrawBorder;
        return this;
    }

    /**
     * �Ƿ񻭱߿�
     *
     * @return true / false
     */
    public boolean isDrawBorder() {
        return drawBorder;
    }

    /**
     * �����ڱ߾��С
     *
     * @param padding �ڱ߾�
     */
    public TableView setPadding(int padding) {
        this.padding = padding;
        return this;
    }

    /**
     * ��ȡ���������
     *
     * @return ���������
     */
    public int getColumnCount() {
        return columnDefineArray.length;
    }

    public static void main(String... args) {


        final TableView tv = new TableView(new ColumnDefine[]{
                new ColumnDefine(10, false, Align.RIGHT),
                new ColumnDefine(0, true, Align.LEFT),
        });

        tv.setDrawBorder(false);
        tv.setPadding(0);

        tv.addRow(
                "AAAAaaaaaaaaaaaaaaaaaaaaaaa",
                "CCCCC"
        );

        tv.addRow(
                "AAAAA",
                "CCC1C\n\n\n3DDDD"

        );

        tv.addRow(
                "AAAAA",
                "CCCCC",
                "DDDDD"
        );


        System.out.println(tv.draw());

    }

}
