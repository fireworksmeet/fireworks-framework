### 该starter提供了数据导出的功能

### 1. `exportSmallData`

> 该方法会将导出的数据以流的形式发送给前端，适用于少量数据。<font color="orange">数据量不是特别大的情况下，建议使用该方法。由于该方法会释放Servlet线程，改用异步线程，因此可以适当延长spring.mvc.async.request-timeout的值，如: 300000(5分钟)</font>

### 2. `exportLargeData`

> 该方法会将导出的数据保存到对象存储上，并返回地址链接；适用于大量数据。<font color="orange">建议将返回的链接存放到数据库，然后定时去清理对象存储上的导出文件</font>
>
> <font color="orange">使用该方法的前提是引入`fireworks-storage-spring-boot-starter`依赖，并添加对象存储相关配置（`fireworks.storage.*`）；该starter基于 Spring Boot 自动装配机制，引入依赖后即自动生效</font>

### 3. 使用MySQL流式查询进行导出的demo

> <font color="orange">**使用前提：**</font>
>
> * 在`mybatis`的mapper中定义返回值为`Cursor<类型可自定义>`的方法
>
>   ```java
>   // 如
>   Cursor<OrderExportDO> getExportData(OrderPageQueryDO param);
>   ```
>
> * 实现上面方法时，需要设置`fetchSize="-2147483648"` 和`resultSetType="FORWARD_ONLY"`
>
>   ```xml
>   <select id="getExportData" resultType="com.fireworks.entity.OrderExportDO" fetchSize="-2147483648" resultSetType="FORWARD_ONLY">
>       SELECT om.id,
>       om.order_str
>       FROM order_main om
>       LEFT JOIN order_detail od ON od.order_id = om.id
>       ORDER BY om.id asc
>   </select>
>   ```
>
> * 实现`ExcelService`类中的`writeData`方法后，调用`exportSmallData`或`exportLargeData`即可完成导出
>
>   ```java
>   package com.fireworks.controller;
>   
>   import com.alibaba.excel.ExcelWriter;
>   import com.alibaba.excel.write.metadata.WriteSheet;
>   import com.yzm.fireworks.api.Result;
>   import com.yzm.fireworks.export.ExcelService;
>   import com.yzm.fireworks.export.ExportService;
>   import com.yzm.fireworks.export.MergeRepeatCellStrategy;
>   import com.fireworks.entity.OrderPageQueryDO;
>   import com.fireworks.entity.PlatformOrderExcelDO;
>   import com.fireworks.mapper.OrderMainMapper;
>   import io.swagger.v3.oas.annotations.Operation;
>   import io.swagger.v3.oas.annotations.tags.Tag;
>   import lombok.AllArgsConstructor;
>   import lombok.Data;
>   import lombok.extern.slf4j.Slf4j;
>   import org.apache.ibatis.session.SqlSession;
>   import org.mybatis.spring.SqlSessionTemplate;
>   import org.springframework.stereotype.Controller;
>   import org.springframework.validation.annotation.Validated;
>   import org.springframework.web.bind.annotation.PostMapping;
>   import org.springframework.web.bind.annotation.ResponseBody;
>   
>   import static com.fireworks.convert.Converter.orderConverter;
>   
>   /**
>    * @author JYuan
>    */
>   @Controller
>   @Slf4j
>   @Validated
>   @Tag(name = "学习模块", description = "StudyController")
>   public class StudyController {
>       private final SqlSessionTemplate sqlSessionTemplate;
>       private final ExportService exportService;
>   
>       public StudyController(SqlSessionTemplate sqlSessionTemplate, ExportService exportService) {
>           this.sqlSessionTemplate = sqlSessionTemplate;
>           this.exportService = exportService;
>       }
>   
>       @ResponseBody
>       @PostMapping("/export")
>       @Operation(summary = "导出")
>       public Result<?> export() {
>           String a = "htd-zhuangtai-xibao-pub";
>           String b = "export";
>           String c = "订单";
>           MergeRepeatCellStrategy strategy = new MergeRepeatCellStrategy(new int[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31});
>           Export2 export2 = new Export2(sqlSessionTemplate);
>           exportService.exportLargeData(a, b, c, PlatformOrderExcelDO.class, export2, new OrderPageQueryDO(), strategy);
>           return Result.ok();
>       }
>       
>       @Data
>       @AllArgsConstructor
>       public static class Export2 implements ExcelService<OrderPageQueryDO> {
>           private final SqlSessionTemplate sqlSessionTemplate;
>   
>           @Override
>           public void writeData(ExcelWriter excelWriter, WriteSheet sheetName, OrderPageQueryDO queryParam) {
>           /*
>               使用流式查询时，不能直接使用容器中的Mapper，因为Mapper的方法在执行完之后连接就关闭了，对应的Cursor也一并关闭了
>               需要使用SqlSession获取需要的Mapper，SqlSession需要调用close方法进行释放。
>            */
>               try (SqlSession sqlSession = sqlSessionTemplate.getSqlSessionFactory().openSession()) {
>                   OrderMainMapper mapper = sqlSession.getMapper(OrderMainMapper.class);
>                   writeData(excelWriter, sheetName, () -> mapper.getExportDataByCursor(queryParam), null, orderConverter::convertPlatform);
>               }
>           }
>       }
>   
>   }
>   ```
>