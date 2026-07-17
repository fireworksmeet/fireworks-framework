package com.yzm.fireworks.export;

import com.alibaba.excel.write.handler.RowWriteHandler;
import com.alibaba.excel.write.handler.WorkbookWriteHandler;
import com.alibaba.excel.write.metadata.holder.WriteSheetHolder;
import com.alibaba.excel.write.metadata.holder.WriteTableHolder;
import com.alibaba.excel.write.metadata.holder.WriteWorkbookHolder;
import com.google.common.collect.Maps;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellRangeAddress;
import org.springframework.util.ObjectUtils;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author JYuan
 */
public class MergeRepeatCellStrategy implements RowWriteHandler, WorkbookWriteHandler {

    /**
     * 主键所在单元格下标
     */
    private final int idIndex;
    /**
     * 当主键重复时，需要合并的单元格列下标
     */
    private final int[] repeatCellIndex;

    /**
     * 存储每一组要合并行的起点和终点
     */
    private final Map<Integer, Integer> firstWithLast;

    /**
     * 当前Sheet对象，用于最后一组合并
     */
    private Sheet sheet;

    /**
     * 每组要合并的第一行值
     */
    private Integer firstRow;

    public MergeRepeatCellStrategy(int[] repeatCellIndex) {
        this(0, repeatCellIndex);
    }

    public MergeRepeatCellStrategy(int idIndex, int[] repeatCellIndex) {
        this.idIndex = idIndex;
        this.repeatCellIndex = repeatCellIndex;
        this.firstWithLast = Maps.newHashMap();
        this.firstRow = -1;
    }

    @Override
    public void afterRowDispose(WriteSheetHolder writeSheetHolder, WriteTableHolder writeTableHolder, Row row, Integer relativeRowIndex, Boolean isHead) {
        if (isHead) {
            return;
        }
        sheet = writeSheetHolder.getSheet();
        // 获取当前行的唯一键所在单元格
        Cell currentCell = row.getCell(idIndex);
        // 获取唯一键
        Object currentId = currentCell.getCellType() == CellType.STRING ? currentCell.getStringCellValue() : currentCell.getNumericCellValue();
        int currentRowNum = row.getRowNum();

        Row preRow = sheet.getRow(currentRowNum - 1);
        // 获取上一行的唯一键所在单元格
        Cell preCell = preRow.getCell(idIndex);
        if (ObjectUtils.isEmpty(preCell)) {
            return;
        }
        // 获取上一行的唯一键
        Object preId = preCell.getCellType() == CellType.STRING ? preCell.getStringCellValue() : preCell.getNumericCellValue();
        if (!ObjectUtils.isEmpty(currentId) && currentId.equals(preId)) {
            addCell(preRow, row);
        } else {
            if (!firstWithLast.isEmpty()) {
                mergeCell(sheet);
                firstWithLast.clear();
                firstRow = -1;
            }
        }
    }

    /**
     * 添加需要合并的单元格
     */
    private void addCell(Row preRow, Row currentRow) {
        if (ObjectUtils.isEmpty(repeatCellIndex)) {
            return;
        }

        if (firstRow == -1) {
            firstRow = preRow.getRowNum();
        }
        firstWithLast.put(firstRow, currentRow.getRowNum());
    }

    /**
     * 合并重复的单元格
     */
    private void mergeCell(Sheet sheet) {
        Integer lastRow = firstWithLast.get(firstRow);

        // 将需要合并的位置只保留第一个单元格的数据
        for (int i = firstRow + 1; i <= lastRow; i++) {
            Row row = sheet.getRow(i);
            Arrays.stream(repeatCellIndex).boxed().forEach(item -> row.getCell(item).setBlank());
        }
        List<CellRangeAddress> list = Arrays.stream(repeatCellIndex).boxed().map(item -> new CellRangeAddress(firstRow, lastRow, item, item)).collect(Collectors.toList());
        for (CellRangeAddress cellAddresses : list) {
            sheet.addMergedRegionUnsafe(cellAddresses);
        }
    }

    @Override
    public void afterWorkbookDispose(WriteWorkbookHolder writeWorkbookHolder) {
        // 判断sheet中是否存在未合并的行(上面的合并逻辑会遗漏掉最后一行的合并，此处逻辑就是合并这最后一行)
        if (!firstWithLast.isEmpty() && !ObjectUtils.isEmpty(sheet)) {
            mergeCell(sheet);
        }
    }
}
