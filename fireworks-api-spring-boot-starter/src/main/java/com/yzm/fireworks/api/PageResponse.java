package com.yzm.fireworks.api;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;
import org.springframework.util.ObjectUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * @author JYuan
 * 分页响应
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
@Accessors(chain = true)
@Schema(title = "分页响应")
public class PageResponse<T> {

    /**
     * 数据
     */
    @Schema(title = "分页数据")
    private List<T> records;

    /**
     * 当前页
     */
    @Schema(title = "当前页")
    private Long current;

    /**
     * pageSize
     */
    @Schema(title = "分页大小")
    private Long size;

    /**
     * 总数
     */
    @Schema(title = "总数")
    private Long total;

    /**
     * 页脚
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private T footer;

    public static <R, T> PageResponse<R> empty(IPage<T> page) {
        PageResponse<R> result = new PageResponse<>();
        result.setCurrent(page.getCurrent());
        result.setSize(page.getSize());
        result.setTotal(page.getTotal());
        result.setRecords(new ArrayList<>());
        return result;
    }

    public static <T> PageResponse<T> create(IPage<T> page) {
        PageResponse<T> response = new PageResponse<>();
        response.setCurrent(page.getCurrent());
        response.setSize(page.getSize());
        response.setTotal(page.getTotal());
        response.setRecords(page.getRecords());
        return response;
    }

    public static <R, T> PageResponse<R> create(IPage<T> page, Function<T, R> function) {
        PageResponse<R> response = new PageResponse<>();
        response.setCurrent(page.getCurrent());
        response.setSize(page.getSize());
        response.setTotal(page.getTotal());

        List<R> records = ObjectUtils.isEmpty(page.getRecords()) ? Collections.emptyList() :
                page.getRecords().stream()
                        .map(function)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toList());
        response.setRecords(records);
        return response;
    }

}
