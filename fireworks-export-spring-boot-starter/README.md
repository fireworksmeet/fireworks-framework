# fireworks-export-spring-boot-starter

基于 EasyExcel 的 Excel 导出能力封装，提供流式导出（小数据、直接回写响应）与「生成文件 + 交由业务落点」两种方式。
框架本身**不绑定任何存储实现**，对象存储、文件中台、本地磁盘都由业务通过 `ExportFileStore` 决定。

## 功能特性

- **流式导出**：`ExcelExporter.exportToStream` 将数据写入任意 `OutputStream`，配合 `ExcelWebUtil.writeToResponse` 以流式响应直接下发（不落盘、不在内存中攒出整个文件）。
- **文件导出（落点可插拔）**：`ExcelExporter.exportToFile` 将大批量数据写入本地临时文件后，交给业务提供的 `ExportFileStore` 落地——直传对象存储、上传文件中台、写本地共享盘皆可，框架只保证临时文件被清理。
- **SPI 而非实现**：`ExportFileStore` 是一个函数式接口，业务用一个 lambda 或 `@Bean` 即可接入自己的文件服务，无需改框架代码。
- **流式批处理**：`ExcelDataProvider` 提供默认流式批处理实现（数据源为任意 `Iterable`，推荐 MyBatis `Cursor`），避免一次性加载全量数据导致 OOM。
- **合并重复单元格**：`RepeatCellMergeStrategy` 支持对指定列做相邻相同值合并。

## 快速开始

### 1. 添加依赖

```xml
<dependency>
    <groupId>com.yzm.fireworks</groupId>
    <artifactId>fireworks-export-spring-boot-starter</artifactId>
    <version>${revision}</version>
</dependency>
```

### 2. 核心接口与上下文

- **`ExportContext<Q>`**：导出上下文，承载文件名、Sheet 名、导出模型 Class、查询参数与自定义写入处理器（不含任何存储概念）。
- **`ExcelDataProvider<T>`**：业务数据提供者，核心方法是 `writeData(excelWriter, writeSheet, queryParam)`。
- **`ExcelExporter`**：导出执行器，提供 `exportToStream`（流式）与 `exportToFile`（生成文件并交给 `ExportFileStore`）两个方法。
- **`ExportFileStore<R>`**：文件落库 SPI（函数式接口），`R save(File file, String contentType)`。返回类型 `R` 完全由业务决定（fileId、下载地址、自定义 DTO），框架只做透传，不定义统一的返回对象。

```java
// 组装导出上下文
ExportContext<OrderPageQueryDO> context = ExportContext.<OrderPageQueryDO>builder()
        .fileName("订单导出列表")          // 基础文件名，会拼 UUID 后缀
        .sheetName("订单")
        .dataClass(OrderExportDO.class)   // 导出模型（@ExcelProperty 标注列）
        .queryParam(query)                // 查询参数
        .build();
```

### 3. Web 下载（小数据量）

通过 `ExcelWebUtil.writeToResponse` 将导出结果直接封装为 `ResponseEntity<StreamingResponseBody>`：

```java
@PostMapping("/export/small")
public ResponseEntity<StreamingResponseBody> exportSmall(@RequestBody OrderPageQueryDO query) {
    ExportContext<OrderPageQueryDO> context = ExportContext.<OrderPageQueryDO>builder()
            .fileName("订单导出")
            .sheetName("订单")
            .dataClass(OrderExportDO.class)
            .queryParam(query)
            .build();
    return ExcelWebUtil.writeToResponse(excelExporter, context, dataProvider);
}
```

> `writeToResponse` 采用异步响应（`StreamingResponseBody`），会释放 Servlet 线程。
> 若导出耗时较长，可适当调大 `spring.mvc.async.request-timeout`，如 `300000`（5 分钟）。

### 4. 大批量导出（落点由业务决定）

`ExcelExporter.exportToFile` 把数据写入本地临时文件后，交给调用方传入的 `ExportFileStore`；
框架不引入任何存储 SDK，也不假设你要传对象存储还是文件中台。

**第一步：提供 `ExportFileStore`（二选一，或用你自己的实现）**

```java
@Configuration
public class ExportFileConfig {

    /**
     * 直传对象存储：需要自行引入 fireworks-storage-spring-boot-starter
     */
    @Bean
    public ExportFileStore<String> objectStorageFileStore(S3StorageService storageService) {
        return (file, contentType) -> {
            String objectKey = "export/" + file.getName();
            StorageFile stored = storageService.putObject(
                    "order-export", objectKey, file, contentType);
            return stored.getUrl();     // 业务返回什么，exportToFile 就返回什么
        };
    }

    /**
     * 或者上传文件中台：返回 fileId，下载地址由中台按权限签发
     */
    @Bean
    public ExportFileStore<Long> fileCenterStore(FileCenterClient client) {
        return (file, contentType) -> client.upload(file, contentType);
    }
}
```

**第二步：调用**

```java
@PostMapping("/export/large")
public Result<String> exportLarge(@RequestBody OrderPageQueryDO query) {
    ExportContext<OrderPageQueryDO> context = ExportContext.<OrderPageQueryDO>builder()
            .fileName("订单导出")
            .sheetName("订单")
            .dataClass(OrderExportDO.class)
            .queryParam(query)
            .build();
    // 泛型 R 由 exportFileStore 决定：这里返回下载地址，换成 ExportFileStore<Long> 就直接返回 fileId
    return Result.ok(excelExporter.exportToFile(context, dataProvider, exportFileStore));
}
```

> **临时文件生命周期**：`file` 是本地临时文件，`save` 返回后框架立即删除它。
> 因此必须在该方法内完成上传，**不能把 `File` 存下来异步处理**。
>
> **文件名只有一个来源**：`file.getName()`，形如 `订单导出列表_3f2a1b7c.xlsx`（业务名里的非法字符会被替换为 `_`），
> `file.length()` 是字节数。不要把它换成「不带随机后缀的漂亮名字」去当对象 key——并发导出会互相覆盖。
>
> **`contentType` 由框架给出**：`File` 拿不到 MIME，`Files.probeContentType` 依赖操作系统映射（容器里常返回 `null`），
> 所以 MIME 统一由框架传入（当前是 xlsx 标准类型），实现方不必也不应自己写死。
>
> **不要持久化预签名 URL**：它带有效期，换桶 / 换域名后立即失效。长期保存请存 `id`（中台 fileId）
> 或 `objectKey`，需要下载时再换取地址。

### 5. 流式查询导出（推荐 MyBatis Cursor）

当数据量很大时，采用数据库流式查询 + 分批刷盘，避免内存溢出。

**前提：**

1. Mapper 方法返回 `Cursor<T>`：

```java
Cursor<OrderExportDO> getExportDataByCursor(OrderPageQueryDO param);
```

2. XML 中设置 `fetchSize="-2147483648"` 与 `resultSetType="FORWARD_ONLY"`：

```xml
<select id="getExportDataByCursor" resultType="com.fireworks.entity.OrderExportDO"
        fetchSize="-2147483648" resultSetType="FORWARD_ONLY">
    SELECT om.id, om.order_str
    FROM order_main om
    ORDER BY om.id asc
</select>
```

3. 实现 `ExcelDataProvider` 的 `writeData`，利用其提供的流式批处理默认方法：

```java
public class OrderExcelDataProvider implements ExcelDataProvider<OrderPageQueryDO> {

    private final SqlSessionTemplate sqlSessionTemplate;

    @Override
    public void writeData(ExcelWriter excelWriter, WriteSheet writeSheet, OrderPageQueryDO queryParam) {
        // Cursor 必须通过独立的 SqlSession 获取，防止 Mapper 方法执行完连接关闭后 Cursor 失效
        try (SqlSession sqlSession = sqlSessionTemplate.getSqlSessionFactory().openSession()) {
            OrderMainMapper mapper = sqlSession.getMapper(OrderMainMapper.class);
            // 默认流式批处理实现：分批读取、转换、刷盘
            writeData(excelWriter, writeSheet,
                    () -> mapper.getExportDataByCursor(queryParam),
                    1000,                       // 批次大小
                    null,                       // 批次数据处理器（可选）
                    orderConverter::convertPlatform); // 数据转换器（可选）
        }
    }
}
```

> 默认流式实现还支持传入 `idExtractor`（如 `OrderMain::getId`），用于解决 MyBatis 一对多 / 嵌套集合映射时
> 同一主记录重复写入的问题。

### 6. 合并重复单元格

若需对导出表格中相邻相同的列值做合并，可注册 `RepeatCellMergeStrategy`：

```java
// 合并第 0~2 列中相邻相同的单元格
RepeatCellMergeStrategy strategy = new RepeatCellMergeStrategy(new int[]{0, 1, 2});
ExportContext<...> context = ExportContext.<...>builder()
        .writeHandler(strategy)
        // ...其他字段
        .build();
```

## 配置

```yaml
fireworks:
  export:
    path: /data/export   # 本地临时文件目录，缺省使用 {java.io.tmpdir}/{spring.application.name}/export
    font-name: 宋体       # 导出字体，容器环境可按需改为 微软雅黑 / Arial 等
    font-size: 11        # 导出字号（磅）
```

## 注意事项

- `exportToFile` 在 `ExportFileStore.save` 返回后**严格清理本地临时文件**，无需手动处理；进程被强杀时可能残留，建议给临时目录配置定期清理。
- `ExportFileStore` 抛出的异常会原样向上传播（临时文件仍会被清理），业务无需再包一层 `try/finally`。
- 导出能力本身不内置行数上限：单次能导多少行属于业务策略，建议在查询入口控制（count 预检、时间区间、分页参数上限），而不是等写入过程中再打断——那时响应可能已经写出半个文件。数据量大时请走 `exportToFile`。
- `ExcelDataProvider` 的流式批处理默认实现依赖 MyBatis `Cursor`，请确保按上述方式使用独立 `SqlSession`。
