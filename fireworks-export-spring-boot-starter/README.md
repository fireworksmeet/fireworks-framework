# fireworks-export-spring-boot-starter

基于 EasyExcel 的 Excel 导出能力封装，提供流式导出（Web 响应）与对象存储导出（大批量落库）两种方式。

## 功能特性

- **流式导出**：`ExcelExporter.exportToStream` 将数据写入任意 `OutputStream`，配合 `ExcelWebUtil.writeToResponse` 可零拷贝下发 Web 响应。
- **对象存储导出**：`ExcelExporter.exportToStorage` 将大批量数据写入本地临时文件后上传至 OSS / MinIO，返回可访问的存储地址。
- **MyBatis Cursor 流式批处理**：`ExcelService` 提供基于 `Cursor` 的默认流式批处理实现，避免一次性加载全量数据导致 OOM。
- **合并重复单元格**：`MergeRepeatCellStrategy` 支持对指定列做相邻相同值合并。

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

- **`ExportContext<Q>`**：导出上下文，承载文件名、Sheet 名、桶名、objectKey、导出模型 Class、查询参数与自定义写入处理器。
- **`ExcelService<T>`**：业务数据提供服务，核心方法是 `writeData(excelWriter, sheetName, queryParam)`。
- **`ExcelExporter`**：导出执行器，提供 `exportToStream`（流式）与 `exportToStorage`（落库）两个方法。

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
    return ExcelWebUtil.writeToResponse(excelExporter, context, excelService);
}
```

> `writeToResponse` 采用异步响应（`StreamingResponseBody`），会释放 Servlet 线程。
> 若导出耗时较长，可适当调大 `spring.mvc.async.request-timeout`，如 `300000`（5 分钟）。

### 4. 大批量导出到对象存储

`ExcelExporter.exportToStorage` 会先将数据写入本地临时文件，再上传至对象存储并返回 `StorageFile`。

```java
@PostMapping("/export/large")
public Result<?> exportLarge(@RequestBody OrderPageQueryDO query) {
    ExportContext<OrderPageQueryDO> context = ExportContext.<OrderPageQueryDO>builder()
            .fileName("订单导出")
            .sheetName("订单")
            .bucket("order-export")              // 可选，缺省用配置的默认桶
            .dataClass(OrderExportDO.class)
            .queryParam(query)
            .build();
    StorageFile file = excelExporter.exportToStorage(context, excelService);
    // file.getUrl() 即为可下载地址，建议入库后定时清理
    return Result.ok(file);
}
```

> **前置依赖**：使用 `exportToStorage` 需同时引入 `fireworks-storage-spring-boot-starter` 并配置对象存储（`fireworks.storage.*`）。
> 建议将返回的下载链接落库，再由定时任务清理对象存储上的历史导出文件。

### 5. 基于 MyBatis Cursor 的流式查询导出

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

3. 实现 `ExcelService` 的 `writeData`，利用其提供的流式批处理默认方法：

```java
public class OrderExcelService implements ExcelService<OrderPageQueryDO> {

    private final SqlSessionTemplate sqlSessionTemplate;

    @Override
    public void writeData(ExcelWriter excelWriter, WriteSheet sheetName, OrderPageQueryDO queryParam) {
        // Cursor 必须通过独立的 SqlSession 获取，防止 Mapper 方法执行完连接关闭后 Cursor 失效
        try (SqlSession sqlSession = sqlSessionTemplate.getSqlSessionFactory().openSession()) {
            OrderMainMapper mapper = sqlSession.getMapper(OrderMainMapper.class);
            // 默认流式批处理实现：分批读取、转换、刷盘
            writeData(excelWriter, sheetName,
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

若需对导出表格中相邻相同的列值做合并，可注册 `MergeRepeatCellStrategy`：

```java
// 合并第 0~2 列中相邻相同的单元格
MergeRepeatCellStrategy strategy = new MergeRepeatCellStrategy(new int[]{0, 1, 2});
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
```

## 注意事项

- `exportToStorage` 上传完成后会**严格清理本地临时文件**，无需手动处理。
- 流式导出（`exportToStream`）对单次数据量无硬性限制，但过大会拖长客户端等待时间，建议大数据量走 `exportToStorage`。
- `ExcelService` 的流式批处理默认实现依赖 MyBatis `Cursor`，请确保按上述方式使用独立 `SqlSession`。
