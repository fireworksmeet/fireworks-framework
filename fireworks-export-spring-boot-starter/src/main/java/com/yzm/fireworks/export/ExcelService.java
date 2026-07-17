package com.yzm.fireworks.export;

import com.alibaba.excel.ExcelWriter;
import com.alibaba.excel.write.metadata.WriteSheet;
import com.google.common.collect.Lists;
import org.apache.ibatis.cursor.Cursor;
import org.springframework.util.ObjectUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * @author JYuan
 */
public interface ExcelService<T> {

    /**
     * 查询数据库数据并写入excel
     *
     * @param excelWriter excelWriter
     * @param sheetName   sheet前缀
     * @param queryParam  查询参数
     */
    void writeData(ExcelWriter excelWriter, WriteSheet sheetName, T queryParam);

    /**
     * 对writeData的粗略实现，可在实现writeData时，直接调用下面方法
     *
     * @param excelWriter   excelWriter
     * @param sheetName     sheet前缀
     * @param selectList    查询数据的方法
     * @param batchSize     每次从流中拿去的数据条数
     * @param dataHandler   查询结果的数据处理器
     * @param dataConverter 查询结果类型和excel数据类型的转换
     * @param <P>           查询的数据类型
     * @param <D>           导出的数据类型
     */
    default <P, D> void writeData(ExcelWriter excelWriter, WriteSheet sheetName, Supplier<Cursor<P>> selectList, int batchSize, Consumer<List<P>> dataHandler, Function<List<P>, List<D>> dataConverter) {
        List<P> caches = Lists.newArrayList();
        // 如果一条数据都没有，则写入一个空的集合
        boolean hasData = true;
        try (Cursor<P> cursor = selectList.get()) {
            // 兼容数据为空的情况
            if (ObjectUtils.isEmpty(cursor)) {
                excelWriter.write(Collections.emptyList(), sheetName);
            } else {
                for (P p : cursor) {
                    hasData = false;
                    // 为了兼容Mybatis使用Cursor联表查询导致嵌套集合未完全填充的问题
                    // 以及可能根据配置导致对同一对象返回多次的问题，进行引用去重
                    if (caches.isEmpty() || caches.getLast() != p) {
                        caches.add(p);
                    }
                    // 延迟一条写入：当caches到达 batchSize + 1 时，说明前 batchSize 个对象已经完全填充
                    if (caches.size() > batchSize) {
                        List<P> writeList = new ArrayList<>(caches.subList(0, batchSize));
                        writeRecords(excelWriter, sheetName, writeList, dataHandler, dataConverter);
                        P last = caches.get(batchSize);
                        caches.clear();
                        caches.add(last);
                    }
                }
                if (!caches.isEmpty()) {
                    writeRecords(excelWriter, sheetName, caches, dataHandler, dataConverter);
                } else if (hasData) {
                    excelWriter.write(Collections.emptyList(), sheetName);
                }
            }

        } catch (IOException e) {
            throw new UnsupportedOperationException(e);
        }
    }

    default <P, D> void writeRecords(ExcelWriter excelWriter, WriteSheet sheetName, List<P> records, Consumer<List<P>> dataHandler, Function<List<P>, List<D>> dataConverter) {
        if (!ObjectUtils.isEmpty(dataHandler)) {
            dataHandler.accept(records);
        }

        if (!ObjectUtils.isEmpty(dataConverter)) {
            List<D> data = dataConverter.apply(records);
            excelWriter.write(data, sheetName);
        } else {
            excelWriter.write(records, sheetName);
        }
    }
}