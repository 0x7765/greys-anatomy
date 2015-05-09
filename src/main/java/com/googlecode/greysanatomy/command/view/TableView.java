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

    public static final int BORDER_TOP = 1 << 0;
    public static final int BORDER_BOTTOM = 1 << 1;

    // �����еĶ���
    private final ColumnDefine[] columnDefineArray;

    // �Ƿ���Ⱦ�߿�
    private boolean isBorder;

    // �߿�
    private int border = BORDER_TOP | BORDER_BOTTOM;

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

    private boolean isBorder(int border) {
        return (this.border & border) == border;
    }

    public int border() {
        return border;
    }

    public TableView border(int border) {
        this.border = border;
        return this;
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

            final boolean isFirstRow = rowIndex == 0;
            final boolean isLastRow = rowIndex == tableHigh - 1;

            // ��ӡ�׷ָ���
            if (isFirstRow
                    && isBorder()
                    && isBorder(BORDER_TOP)) {
                tableSB.append(drawSeparationLine(widthCacheArray)).append("\n");
            }

            // ��ӡ�ڲ��ָ���
            if (!isFirstRow
                    && isBorder()) {
                tableSB.append(drawSeparationLine(widthCacheArray)).append("\n");
            }

            // ��һ��
            tableSB.append(drawRow(widthCacheArray, rowIndex));


            // ��ӡ��β�ָ���
            if (isLastRow
                    && isBorder()
                    && isBorder(BORDER_BOTTOM)) {
                // ��ӡ�ָ���
                tableSB.append(drawSeparationLine(widthCacheArray)).append("\n");
            }

        }


        return tableSB.toString();
    }


    private String drawRow(int[] widthCacheArray, int rowIndex) {

        final StringBuilder rowSB = new StringBuilder();
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

                    final String borderChar = isBorder() ? "|" : EMPTY;
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
                    rowSB.append(segmentSB);
                }

            } while (hasNext);

            return rowSB.toString();
        } finally {
            for (Scanner scanner : scannerArray) {
                if (null != scanner) {
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
        return separationLineSB
                .append("+")
                .toString();
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
     * @param isBorder true / false
     */
    public TableView setBorder(boolean isBorder) {
        this.isBorder = isBorder;
        return this;
    }

    /**
     * �Ƿ񻭱߿�
     *
     * @return true / false
     */
    public boolean isBorder() {
        return isBorder;
    }

    /**
     * �����ڱ߾��С
     *
     * @param padding �ڱ߾�
     */
    public TableView padding(int padding) {
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

        tv.setBorder(true);
        tv.padding(1);

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


        tv.border(tv.border() & ~BORDER_TOP);
        System.out.print(tv.draw());
        System.out.print(tv.draw());

    }

}
