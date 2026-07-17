package com.yzm.fireworks.export;

import com.alibaba.excel.write.handler.WriteHandler;
import com.yzm.fireworks.storage.api.StorageFile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

/**
 * @author JYuan
 */
public interface ExportService {


    /**
     * 将少量数据写入excel
     *
     * @param excelFileName 生成的excel文件名
     * @param dataClass     写入数据对应的类名
     * @param excelService  实现writeData的service
     * @param queryParam    查询参数
     * @return StreamingResponseBody 会直接把流写入到response的输出流中，并且不会占用Servlet容器线程
     */
    <D, Q> ResponseEntity<StreamingResponseBody> exportSmallData(String excelFileName, Class<D> dataClass, ExcelService<Q> excelService, Q queryParam);

    /**
     * 将少量数据写入excel
     *
     * @param excelFileName 生成的excel文件名
     * @param sheetName     sheet名
     * @param dataClass     写入数据对应的类名
     * @param excelService  实现writeData的service
     * @param queryParam    查询参数
     * @return StreamingResponseBody 会直接把流写入到response的输出流中，并且不会占用Servlet容器线程
     */
    <D, Q> ResponseEntity<StreamingResponseBody> exportSmallData(String excelFileName, String sheetName, Class<D> dataClass, ExcelService<Q> excelService, Q queryParam);

    /**
     * 将少量数据写入excel
     *
     * @param excelFileName 生成的excel文件名
     * @param dataClass     写入数据对应的类名
     * @param excelService  实现writeData的service
     * @param queryParam    查询参数
     * @param writeHandler  写入策略
     * @return StreamingResponseBody 会直接把流写入到response的输出流中，并且不会占用Servlet容器线程
     */
    <D, Q> ResponseEntity<StreamingResponseBody> exportSmallData(String excelFileName, Class<D> dataClass, ExcelService<Q> excelService, Q queryParam, WriteHandler writeHandler);

    /**
     * 将少量数据写入excel
     *
     * @param excelFileName 生成的excel文件名
     * @param sheetName     sheet名
     * @param dataClass     写入数据对应的类名
     * @param excelService  实现writeData的service
     * @param queryParam    查询参数
     * @param writeHandler  写入策略
     * @return StreamingResponseBody 会直接把流写入到response的输出流中，并且不会占用Servlet容器线程
     */
    <D, Q> ResponseEntity<StreamingResponseBody> exportSmallData(String excelFileName, String sheetName, Class<D> dataClass, ExcelService<Q> excelService, Q queryParam, WriteHandler writeHandler);

    /**
     * 将大量数据写入excel并上传OSS
     *
     * @param bucketName    bucketName
     * @param dir           OSS上存放的文件夹名称，多级目录用 /
     * @param excelFileName 生成的excel文件名
     * @param dataClass     写入数据对应的类名
     * @param excelService  实现writeData的service
     * @param queryParam    查询参数
     * @return 返回相关文件参数
     */
    <D, Q> StorageFile exportLargeData(String bucketName, String dir, String excelFileName, Class<D> dataClass, ExcelService<Q> excelService, Q queryParam);

    /**
     * 将大量数据写入excel并上传OSS
     *
     * @param bucketName    bucketName
     * @param dir           OSS上存放的文件夹名称，多级目录用 /
     * @param excelFileName 生成的excel文件名
     * @param sheetName     sheet名
     * @param dataClass     写入数据对应的类名
     * @param excelService  实现writeData的service
     * @param queryParam    查询参数
     * @return 返回相关文件参数
     */
    <D, Q> StorageFile exportLargeData(String bucketName, String dir, String excelFileName, String sheetName, Class<D> dataClass, ExcelService<Q> excelService, Q queryParam);

    /**
     * 将大量数据写入excel并上传OSS
     *
     * @param bucketName    bucketName
     * @param dir           OSS上存放的文件夹名称，多级目录用 /
     * @param excelFileName 生成的excel文件名
     * @param dataClass     写入数据对应的类名
     * @param excelService  实现writeData的service
     * @param queryParam    查询参数
     * @param writeHandler  写入策略
     * @return 返回相关文件参数
     */
    <D, Q> StorageFile exportLargeData(String bucketName, String dir, String excelFileName, Class<D> dataClass, ExcelService<Q> excelService, Q queryParam, WriteHandler writeHandler);

    /**
     * 将大量数据写入excel并上传OSS
     *
     * @param bucketName    bucketName
     * @param dir           OSS上存放的文件夹名称，多级目录用 /
     * @param excelFileName 生成的excel文件名
     * @param sheetName     sheet名
     * @param dataClass     写入数据对应的类名
     * @param excelService  实现writeData的service
     * @param queryParam    查询参数
     * @param writeHandler  写入策略
     * @return 返回相关文件参数
     */
    <D, Q> StorageFile exportLargeData(String bucketName, String dir, String excelFileName, String sheetName, Class<D> dataClass, ExcelService<Q> excelService, Q queryParam, WriteHandler writeHandler);
}