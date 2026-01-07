package com.monitor.backend.controller;

import com.monitor.backend.common.ApiResponse;
import com.monitor.backend.exception.ErrorCode;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 自定义错误控制器
 * <p>
 * 处理 Spring 默认错误页面，特别是处理 SSE/WebSocket 等特殊 Content-Type 的错误响应。
 * 解决 "No converter for [class java.util.LinkedHashMap] with preset Content-Type 'text/event-stream'" 问题。
 * </p>
 */
@RestController
public class CustomErrorController implements ErrorController {

    @RequestMapping(value = "/error", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<Void>> handleError(HttpServletRequest request) {
        // 获取错误状态码
        Object status = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        Object message = request.getAttribute(RequestDispatcher.ERROR_MESSAGE);
        Object exception = request.getAttribute(RequestDispatcher.ERROR_EXCEPTION);
        
        HttpStatus httpStatus = HttpStatus.INTERNAL_SERVER_ERROR;
        String errorMessage = "系统内部错误";
        
        if (status != null) {
            int statusCode = Integer.parseInt(status.toString());
            httpStatus = HttpStatus.resolve(statusCode);
            if (httpStatus == null) {
                httpStatus = HttpStatus.INTERNAL_SERVER_ERROR;
            }
            
            // 根据状态码设置错误信息
            switch (statusCode) {
                case 400:
                    errorMessage = "请求参数错误";
                    break;
                case 401:
                    errorMessage = "未授权，请登录";
                    break;
                case 403:
                    errorMessage = "访问被拒绝";
                    break;
                case 404:
                    errorMessage = "请求的资源不存在";
                    break;
                case 405:
                    errorMessage = "请求方法不支持";
                    break;
                case 500:
                    errorMessage = "系统内部错误，请稍后重试";
                    break;
                default:
                    if (message != null && !message.toString().isEmpty()) {
                        errorMessage = message.toString();
                    }
            }
        }
        
        // 如果有异常信息，记录日志（异常已经在GlobalExceptionHandler中处理过了）
        ErrorCode errorCode = mapHttpStatusToErrorCode(httpStatus);
        ApiResponse<Void> response = ApiResponse.fail(errorCode, errorMessage);
        
        return new ResponseEntity<>(response, httpStatus);
    }
    
    /**
     * 将HTTP状态码映射到业务错误码
     */
    private ErrorCode mapHttpStatusToErrorCode(HttpStatus httpStatus) {
        return switch (httpStatus.value()) {
            case 400 -> ErrorCode.PARAM_INVALID;
            case 401 -> ErrorCode.UNAUTHORIZED;
            case 403 -> ErrorCode.FORBIDDEN;
            case 404 -> ErrorCode.NOT_FOUND;
            case 405 -> ErrorCode.METHOD_NOT_ALLOWED;
            default -> ErrorCode.INTERNAL_ERROR;
        };
    }
}
