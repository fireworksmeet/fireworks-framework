package com.yzm.fireworks.export.cursor;

import com.alibaba.excel.ExcelWriter;
import com.alibaba.excel.write.metadata.WriteSheet;
import org.apache.ibatis.cursor.Cursor;
import org.springframework.util.ObjectUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Excel 导出业务数据提供服务接口
 *
 * @author JYuan
 */
public interface ExcelService<T> {

    /**
     * 查询数据库数据并写入 Excel
     *
     * @param excelWriter excelWriter 写入器
     * @param sheetName   WriteSheet 配置对象
     * @param queryParam  查询参数
     */
    void writeData(ExcelWriter excelWriter, WriteSheet sheetName, T queryParam);

    /**
     * 基于 MyBatis Cursor 的流式批处理导出默认实现
     */
    default <P, D> void writeData(ExcelWriter excelWriter,
                                  WriteSheet sheetName,
                                  Supplier<Cursor<P>> selectList,
                                  int batchSize,
                                  Consumer<List<P>> dataHandler,
                                  Function<List<P>, List<D>> dataConverter) {
        writeData(excelWriter, sheetName, selectList, batchSize, dataHandler, dataConverter, null);
    }

    /**
     * 基于 MyBatis Cursor 的流式批处理导出（带自定义主键去重提取器）
     *
     * @param excelWriter   excelWriter
     * @param sheetName     sheet 配置
     * @param selectList    数据源 Supplier
     * @param batchSize     批次刷盘大小
     * @param dataHandler   批次数据处理器（可选）
     * @param dataConverter 数据转换器（可选）
     * @param idExtractor   主键提取器（如 User::getId，用于解决 MyBatis 一对多/嵌套集合映射时的去重隐患，可选）
     */
    default <P, D> void writeData(ExcelWriter excelWriter,
                                  WriteSheet sheetName,
                                  Supplier<Cursor<P>> selectList,
                                  int batchSize,
                                  Consumer<List<P>> dataHandler,
                                  Function<List<P>, List<D>> dataConverter,
                                  Function<P, Object> idExtractor) {

        try (Cursor<P> cursor = selectList.get()) {
            // 1. 兼容 Cursor 本身为空的情况
            if (ObjectUtils.isEmpty(cursor)) {
                excelWriter.write(Collections.emptyList(), sheetName);
                return;
            }

            List<P> buffer = new ArrayList<>(batchSize + 1);
            boolean hasWrittenRecords = false;

            for (P current : cursor) {
                if (current == null) {
                    continue;
                }

                // 2. 避免重复追加同一记录（兼容 MyBatis 嵌套集合映射场景）
                if (buffer.isEmpty() || !isSameRecord(buffer.getLast(), current, idExtractor)) {
                    buffer.add(current);
                }

                // 3. 延迟 1 条刷盘：当 buffer 达到 batchSize + 1 时，说明前 batchSize 条记录的嵌套集合已填充完毕
                if (buffer.size() > batchSize) {
                    List<P> batchToFlush = new ArrayList<>(buffer.subList(0, batchSize));
                    writeRecords(excelWriter, sheetName, batchToFlush, dataHandler, dataConverter);
                    hasWrittenRecords = true;

                    // 保留未确定完全填充的最后一条记录
                    P lastPendingItem = buffer.get(batchSize);
                    buffer.clear();
                    buffer.add(lastPendingItem);
                }
            }

            // 4. 清空末尾剩余的缓冲区数据
            if (!buffer.isEmpty()) {
                writeRecords(excelWriter, sheetName, buffer, dataHandler, dataConverter);
                hasWrittenRecords = true;
            }

            // 5. 整个 Cursor 没有任何有效数据时，写入空列表以生成表格 Header
            if (!hasWrittenRecords) {
                excelWriter.write(Collections.emptyList(), sheetName);
            }

        } catch (IOException e) {
            throw new IllegalStateException("Failed to read data from MyBatis Cursor", e);
        }
    }

    /**
     * 单批次数据写入处理
     */
    default <P, D> void writeRecords(ExcelWriter excelWriter,
                                    WriteSheet sheetName,
                                    List<P> records,
                                    Consumer<List<P>> dataHandler,
                                    Function<List<P>, List<D>> dataConverter) {
        if (ObjectUtils.isEmpty(records)) {
            return;
        }

        if (dataHandler != null) {
            dataHandler.accept(records);
        }

        if (dataConverter != null) {
            List<D> convertedData = dataConverter.apply(records);
            excelWriter.write(convertedData, sheetName);
        } else {
            excelWriter.write(records, sheetName);
        }
    }

    /**
     * 内部辅助方法：校验两条记录是否代表同一个主对象
     */
    private <P> boolean isSameRecord(P previous, P current, Function<P, Object> idExtractor) {
        if (previous == current) {
            return true;
        }
        if (idExtractor != null) {
            Object prevId = idExtractor.apply(previous);
            Object currId = idExtractor.apply(current);
            return prevId != null && prevId.equals(currId);
        }
        return Objects.equals(previous, current);
    }
}