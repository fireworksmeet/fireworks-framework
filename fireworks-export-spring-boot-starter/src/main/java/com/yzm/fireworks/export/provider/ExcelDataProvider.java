package com.yzm.fireworks.export.provider;

import com.alibaba.excel.ExcelWriter;
import com.alibaba.excel.write.metadata.WriteSheet;
import org.springframework.util.ObjectUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * 导出数据提供者 SPI。
 * <p>
 * 业务方实现 {@link #writeData(ExcelWriter, WriteSheet, Object)} 提供数据，并可复用本接口提供的
 * 流式批处理模板：按 {@code batchSize} 分批刷盘，避免一次性加载全量数据。
 * <p>
 * <b>与任何 ORM 无关</b>：流式数据源以 {@link Iterable} 表达，因此 MyBatis 的 {@code Cursor}、
 * 自定义迭代器都可以直接接入（{@code Cursor} 本身即 {@code Iterable}）。
 * 若数据源同时是 {@link AutoCloseable}（MyBatis {@code Cursor} 即是），框架会在消费结束后尽力关闭它。
 *
 * @author JYuan
 */
public interface ExcelDataProvider<T> {

    /**
     * 查询数据并写入 Excel
     *
     * @param excelWriter excelWriter 写入器
     * @param writeSheet  WriteSheet 配置对象
     * @param queryParam  查询参数
     */
    void writeData(ExcelWriter excelWriter, WriteSheet writeSheet, T queryParam);

    /**
     * 流式批处理导出默认实现
     */
    default <P, D> void writeData(ExcelWriter excelWriter,
                                  WriteSheet writeSheet,
                                  Supplier<? extends Iterable<P>> selectList,
                                  int batchSize,
                                  Consumer<List<P>> dataHandler,
                                  Function<List<P>, List<D>> dataConverter) {
        writeData(excelWriter, writeSheet, selectList, batchSize, dataHandler, dataConverter, null);
    }

    /**
     * 流式批处理导出（带自定义主键去重提取器）
     *
     * @param excelWriter   excelWriter
     * @param writeSheet    sheet 配置
     * @param selectList    流式数据源 Supplier（如 {@code () -> mapper.getExportDataByCursor(param)}）
     * @param batchSize     批次刷盘大小
     * @param dataHandler   批次数据处理器（可选）
     * @param dataConverter 数据转换器（可选）
     * @param idExtractor   主键提取器（如 User::getId，用于解决一对多/嵌套集合映射时的去重隐患，可选）
     */
    default <P, D> void writeData(ExcelWriter excelWriter,
                                  WriteSheet writeSheet,
                                  Supplier<? extends Iterable<P>> selectList,
                                  int batchSize,
                                  Consumer<List<P>> dataHandler,
                                  Function<List<P>, List<D>> dataConverter,
                                  Function<P, Object> idExtractor) {

        Iterable<P> source = selectList.get();
        try {
            // 1. 兼容数据源本身为空的情况
            if (source == null) {
                excelWriter.write(Collections.emptyList(), writeSheet);
                return;
            }

            List<P> buffer = new ArrayList<>(batchSize + 1);
            boolean hasWrittenRecords = false;

            for (P current : source) {
                if (current == null) {
                    continue;
                }

                // 2. 避免重复追加同一记录（兼容一对多/嵌套集合映射场景）
                if (buffer.isEmpty() || !isSameRecord(buffer.getLast(), current, idExtractor)) {
                    buffer.add(current);
                }

                // 3. 延迟 1 条刷盘：当 buffer 达到 batchSize + 1 时，说明前 batchSize 条记录的嵌套集合已填充完毕
                if (buffer.size() > batchSize) {
                    List<P> batchToFlush = new ArrayList<>(buffer.subList(0, batchSize));
                    writeRecords(excelWriter, writeSheet, batchToFlush, dataHandler, dataConverter);
                    hasWrittenRecords = true;

                    // 保留未确定完全填充的最后一条记录
                    P lastPendingItem = buffer.get(batchSize);
                    buffer.clear();
                    buffer.add(lastPendingItem);
                }
            }

            // 4. 清空末尾剩余的缓冲区数据
            if (!buffer.isEmpty()) {
                writeRecords(excelWriter, writeSheet, buffer, dataHandler, dataConverter);
                hasWrittenRecords = true;
            }

            // 5. 整个数据源没有任何有效数据时，写入空列表以生成表格 Header
            if (!hasWrittenRecords) {
                excelWriter.write(Collections.emptyList(), writeSheet);
            }

        } finally {
            closeQuietly(source);
        }
    }

    /**
     * 单批次数据写入处理
     */
    default <P, D> void writeRecords(ExcelWriter excelWriter,
                                    WriteSheet writeSheet,
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
            excelWriter.write(convertedData, writeSheet);
        } else {
            excelWriter.write(records, writeSheet);
        }
    }

    /**
     * 尽力关闭流式数据源（若其支持 {@link AutoCloseable}）。
     * <p>关闭失败不覆盖主流程结果；底层资源（如 SqlSession）的生命周期最终由调用方保证。
     */
    private static void closeQuietly(Iterable<?> source) {
        if (source instanceof AutoCloseable closeable) {
            try {
                closeable.close();
            } catch (Exception ignored) {
                // 尽最大努力关闭；失败不影响已写入的导出结果
            }
        }
    }

    /**
     * 内部辅助方法：校验两条记录是否代表同一个主对象。
     * <p>只有提供 {@code idExtractor} 时才做合并；否则一律视为不同记录。
     */
    private <P> boolean isSameRecord(P previous, P current, Function<P, Object> idExtractor) {
        if (previous == current) {
            return true;
        }
        if (idExtractor == null) {
            // 没有主键提取器时不能按 equals 合并：两条字段完全相同的独立记录会被当成
            // 「同一主记录的嵌套行」而丢弃，导出结果少数据且无任何提示
            return false;
        }
        Object prevId = idExtractor.apply(previous);
        Object currId = idExtractor.apply(current);
        return prevId != null && prevId.equals(currId);
    }
}
