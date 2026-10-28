package com.yzm.fireworks.export.core;

import com.yzm.fireworks.export.file.ExportFileStore;
import com.yzm.fireworks.export.model.ExportContext;
import com.yzm.fireworks.export.provider.ExcelDataProvider;

import java.io.OutputStream;

/**
 * 核心导出服务接口（仅关注流与文件的生成，不感知 HTTP 概念与存储实现）
 *
 * @author JYuan
 */
public interface ExcelExporter {

    /**
     * 将 Excel 数据流直接写入指定的 OutputStream（支持 Web 响应流、文件输出流等）。
     * <p>
     * 适合小数据量：数据边读边写，不落盘；调用方自行负责限额与超时。
     * <p><b>本方法不关闭 {@code outputStream}</b>：导出结束后仍可继续往同一个流里写
     * （如包一层 {@code ZipOutputStream}）。流的生命周期由调用方管理。
     */
    <Q> void exportToStream(OutputStream outputStream, ExportContext<Q> context, ExcelDataProvider<Q> dataProvider);

    /**
     * 将大批量数据写入本地临时文件，再交给 {@link ExportFileStore} 落地。
     * <p>
     * 框架只负责「生成文件 + 保证临时文件被清理」，文件最终去哪里（对象存储、文件中台、本地）
     * 完全由传入的 {@code fileStore} 决定，因此换存储方案不需要改框架代码。
     *
     * @param dataProvider 导出数据提供者，由业务实现
     * @param fileStore    文件落库实现，由业务提供；不得缓存临时文件异步处理
     * @param <Q>          查询参数类型
     * @param <R>          落库结果类型，由 {@code fileStore} 决定，框架原样透传
     * @return {@code fileStore.save(...)} 的返回值（文件 ID / 下载地址 / 自定义 DTO）
     */
    <Q, R> R exportToFile(ExportContext<Q> context, ExcelDataProvider<Q> dataProvider, ExportFileStore<R> fileStore);
}
