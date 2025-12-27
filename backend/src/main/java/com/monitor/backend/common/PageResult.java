package com.monitor.backend.common;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 分页响应封装类
 */
@Data
@Schema(description = "分页结果")
public class PageResult<T> {
    
    @Schema(description = "数据列表")
    private List<T> data;
    
    @Schema(description = "总记录数")
    private long total;
    
    @Schema(description = "当前页码")
    private int page;
    
    @Schema(description = "每页大小")
    private int size;
    
    @Schema(description = "总页数")
    private int totalPages;
    
    public PageResult() {
    }
    
    public PageResult(List<T> data, long total, int page, int size) {
        this.data = data;
        this.total = total;
        this.page = page;
        this.size = size;
        this.totalPages = size > 0 ? (int) Math.ceil((double) total / size) : 0;
    }
    
    /**
     * 创建分页结果
     */
    public static <T> PageResult<T> of(List<T> data, long total, int page, int size) {
        return new PageResult<>(data, total, page, size);
    }
}
