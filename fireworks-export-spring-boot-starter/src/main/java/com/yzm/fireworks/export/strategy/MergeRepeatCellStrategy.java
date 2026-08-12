package com.yzm.fireworks.export.strategy;

import com.alibaba.excel.write.handler.RowWriteHandler;
import com.alibaba.excel.write.handler.WorkbookWriteHandler;
import com.alibaba.excel.write.metadata.holder.WriteSheetHolder;
import com.alibaba.excel.write.metadata.holder.WriteTableHolder;
import com.alibaba.excel.write.metadata.holder.WriteWorkbookHolder;
import com.yzm.fireworks.common.constants.StringPool;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellRangeAddress;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * 通用重复列合并策略
 * <p>
 * 注意：由于内部维护了导出的状态上下文，请勿将本类声明为单例 Bean 复用，
 * 每次导出请实例化新的 Strategy 对象（例如：new MergeRepeatCellStrategy(...)）。
 * </p>
 *
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
    private final int[] repeatCellIndices;

    /**
     * POI 标准单元格格式化工具（避免数值型 ID 丢失精度）
     */
    private final DataFormatter dataFormatter = new DataFormatter();

    /**
     * 按 Sheet 维护各自的合并状态
     */
    private final Map<Sheet, SheetMergeContext> sheetContextMap = new IdentityHashMap<>();

    public MergeRepeatCellStrategy(int[] repeatCellIndices) {
        this(0, repeatCellIndices);
    }

    public MergeRepeatCellStrategy(int idIndex, int[] repeatCellIndices) {
        this.idIndex = idIndex;
        this.repeatCellIndices = repeatCellIndices != null ? repeatCellIndices : new int[0];
    }

    @Override
    public void afterRowDispose(WriteSheetHolder writeSheetHolder, WriteTableHolder writeTableHolder, Row row, Integer relativeRowIndex, Boolean isHead) {
        if (Boolean.TRUE.equals(isHead) || row == null) {
            return;
        }

        Sheet sheet = writeSheetHolder.getSheet();
        SheetMergeContext context = sheetContextMap.computeIfAbsent(sheet, k -> new SheetMergeContext());
        // 获取当前行的唯一键所在单元格
        Cell currentIdCell = row.getCell(idIndex);
        // 获取唯一键
        String currentId = getCellValueAsString(currentIdCell);
        int currentRowNum = row.getRowNum();

        // 1. 若当前行无有效 ID，触发结算上一次的合并组
        if (currentId.isEmpty()) {
            context.flushMerge(sheet, repeatCellIndices);
            return;
        }

        // 2. 当前 Sheet 的第一条数据行，初始化组状态
        if (context.lastId == null) {
            context.initGroup(currentId, currentRowNum);
            return;
        }

        // 3. 比较 ID
        if (currentId.equals(context.lastId)) {
            context.lastRow = currentRowNum;
            // 【关键优化】：在当前行写入时即刻清空重复单元格内容，完美兼容 SXSSF 流式刷盘
            clearRepeatCells(row);
        } else {
            // 主键变更，结算上一组数据，并开启新组
            context.flushMerge(sheet, repeatCellIndices);
            context.initGroup(currentId, currentRowNum);
        }
    }

    @Override
    public void afterWorkbookDispose(WriteWorkbookHolder writeWorkbookHolder) {
        // 合并每个Sheet最后一组合并，并移除 Context 防止内存泄露
        sheetContextMap.forEach((sheet, context) -> context.flushMerge(sheet, repeatCellIndices));
        sheetContextMap.clear();
    }

    /**
     * 即时清空当前重复行的指定列
     */
    private void clearRepeatCells(Row row) {
        for (int colIndex : repeatCellIndices) {
            Cell cell = row.getCell(colIndex);
            if (cell != null) {
                cell.setBlank();
            }
        }
    }

    private String getCellValueAsString(Cell cell) {
        if (cell == null) {
            return StringPool.EMPTY;
        }
        return dataFormatter.formatCellValue(cell).trim();
    }

    /**
     * 单个 Sheet 的合并上下文
     */
    private static class SheetMergeContext {
        String lastId = null;
        int firstRow = -1;
        int lastRow = -1;

        void initGroup(String id, int rowNum) {
            this.lastId = id;
            this.firstRow = rowNum;
            this.lastRow = rowNum;
        }

        void flushMerge(Sheet sheet, int[] repeatCellIndices) {
            if (firstRow != -1 && lastRow > firstRow) {
                // 执行批量合并区域注册（单元格内容已在 afterRowDispose 中即时清空）
                for (int colIndex : repeatCellIndices) {
                    sheet.addMergedRegionUnsafe(new CellRangeAddress(firstRow, lastRow, colIndex, colIndex));
                }
            }
            // 重置状态
            this.lastId = null;
            this.firstRow = -1;
            this.lastRow = -1;
        }
    }
}