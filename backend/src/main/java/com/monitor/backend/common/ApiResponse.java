package com.monitor.backend.common;

import com.monitor.backend.exception.ErrorCode;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 统一API响应封装类
 */
@Data
@Schema(description = "统一API响应")
public class ApiResponse<T> {
    
    @Schema(description = "是否成功")
    private boolean success;
    
    @Schema(description = "状态码")
    private int code;
    
    @Schema(description = "消息")
    private String message;
    
    @Schema(description = "响应数据")
    private T data;
    
    public ApiResponse() {
    }
    
    public ApiResponse(boolean success, int code, String message, T data) {
        this.success = success;
        this.code = code;
        this.message = message;
        this.data = data;
    }
    
    // ==================== 静态工厂方法 ====================
    
    /**
     * 成功响应（无数据）
     */
    public static <T> ApiResponse<T> ok() {
        return new ApiResponse<>(true, ErrorCode.SUCCESS.getCode(), ErrorCode.SUCCESS.getMessage(), null);
    }
    
    /**
     * 成功响应（带数据）
     */
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, ErrorCode.SUCCESS.getCode(), ErrorCode.SUCCESS.getMessage(), data);
    }
    
    /**
     * 成功响应（带消息和数据）
     */
    public static <T> ApiResponse<T> ok(String message, T data) {
        return new ApiResponse<>(true, ErrorCode.SUCCESS.getCode(), message, data);
    }
    
    /**
     * 失败响应（使用错误码）
     */
    public static <T> ApiResponse<T> fail(ErrorCode errorCode) {
        return new ApiResponse<>(false, errorCode.getCode(), errorCode.getMessage(), null);
    }
    
    /**
     * 失败响应（使用错误码和自定义消息）
     */
    public static <T> ApiResponse<T> fail(ErrorCode errorCode, String message) {
        return new ApiResponse<>(false, errorCode.getCode(), message, null);
    }
    
    /**
     * 失败响应（自定义状态码和消息）
     */
    public static <T> ApiResponse<T> fail(int code, String message) {
        return new ApiResponse<>(false, code, message, null);
    }
    
    /**
     * 系统错误响应
     */
    public static <T> ApiResponse<T> error(String message) {
        return new ApiResponse<>(false, ErrorCode.INTERNAL_ERROR.getCode(), message, null);
    }

}
