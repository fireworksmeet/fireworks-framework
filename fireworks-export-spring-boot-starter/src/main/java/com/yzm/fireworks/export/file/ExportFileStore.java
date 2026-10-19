package com.yzm.fireworks.export.file;

import java.io.File;

/**
 * 导出文件落库 SPI：决定生成好的文件交给谁。
 * <p>
 * 框架<b>不提供任何实现</b>，因为这属于部署环境相关的决策：
 * <ul>
 *     <li>直传对象存储：用 {@code fireworks-storage} 的 {@code S3StorageService} 上传；</li>
 *     <li>文件中台：按中台协议上传 / 换取 fileId；</li>
 *     <li>本地磁盘 / 共享存储：移动到目标目录。</li>
 * </ul>
 * <p>
 * <b>返回值就是业务自己的类型</b>（{@code Long} 文件 ID、{@code String} 下载地址、或自定义 DTO）：
 * 框架不做任何解释，只会原样透传出去。框架刻意不定义统一的返回对象——各系统对"文件标识"的定义不同，
 * 硬凑一个 id / url / objectKey 的并集只会让业务多一层转换。
 * <p>
 * <b>文件名只有一个来源：{@code file.getName()}</b>。形如 {@code 订单导出列表_3f2a1b7c.xlsx}，
 * 已同时包含业务名与随机后缀。框架不再额外传"展示名"，避免出现两套名字（一套用于展示、一套用于存储）
 * 时对不上；也不要把这里的名字换成不带随机后缀的"漂亮名字"去当对象 key——并发导出会互相覆盖。
 * <p>
 * <b>调用约定</b>：{@code file} 是本地临时文件，方法返回后框架会立即删除它；
 * 实现必须在此方法内完成读取 / 上传，<b>不得把 {@link File} 存起来异步处理</b>。
 * 实现抛出的异常会原样向上传播（临时文件仍会被清理）。
 * <p>
 * <b>为什么方法叫 {@code save} 而不是 {@code uploadFile}</b>：这个 SPI 表达的是「文件落地」，
 * 上传只是其中一种落地方式（本地共享盘就不存在"上传"）。若只支持上传，接口名也应改成
 * {@code ExportFileUploader}，而不是出现 {@code Store.upload} 这种混搭。
 *
 * @param <R> 落库结果类型，由实现决定
 * @author JYuan
 */
@FunctionalInterface
public interface ExportFileStore<R> {

    /**
     * 保存导出文件。
     * <p>
     * 文件名、大小从 {@code file} 获取（{@code file.getName()}、{@code file.length()}）；
     * {@code contentType} 由框架给出——{@link File} 拿不到 MIME，{@code Files.probeContentType}
     * 又依赖操作系统映射（容器里常返回 {@code null}），让每个实现各自写死只会各写各的。
     *
     * @param file        本地临时文件（文件名为「业务名_随机后缀.xlsx」），方法返回后即被删除
     * @param contentType 内容类型，当前为 xlsx 的标准 MIME
     * @return 业务自己的落库结果（文件 ID / 下载地址 / 自定义 DTO）
     */
    R save(File file, String contentType);
}
