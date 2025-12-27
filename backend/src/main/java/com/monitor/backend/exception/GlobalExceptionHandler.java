package com.monitor.backend.exception;

import com.monitor.backend.common.ApiResponse;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 全局异常处理器
 * <p>
 * 统一处理所有Controller层抛出的异常，返回标准化的 {@link ApiResponse} 格式。
 * </p>
 *
 * @author monitor-system
 * @see ApiResponse
 * @see BusinessException
 */
@RestControllerAdvice
public class GlobalExceptionHandler {
    
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    
    // ==================== 业务异常处理 ====================
    
    /**
     * 处理业务异常
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException e) {
        log.warn("业务异常: code={}, message={}", e.getCode(), e.getMessage());
        
        ApiResponse<Void> response = ApiResponse.fail(e.getErrorCode(), e.getMessage());
        
        // 根据错误码确定HTTP状态码
        HttpStatus status = mapToHttpStatus(e.getErrorCode());
        return new ResponseEntity<>(response, status);
    }
    
    // ==================== 参数验证异常处理 ====================
    
    /**
     * 处理 @Valid 校验失败异常 (RequestBody)
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodArgumentNotValidException(
            MethodArgumentNotValidException e) {
        
        List<FieldError> fieldErrors = e.getBindingResult().getFieldErrors();
        String message = fieldErrors.stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining("; "));
        
        log.warn("参数校验失败: {}", message);
        
        ApiResponse<Void> response = ApiResponse.fail(ErrorCode.PARAM_INVALID, message);
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }
    
    /**
     * 处理 @Valid 校验失败异常 (表单)
     */
    @ExceptionHandler(BindException.class)
    public ResponseEntity<ApiResponse<Void>> handleBindException(BindException e) {
        List<FieldError> fieldErrors = e.getBindingResult().getFieldErrors();
        String message = fieldErrors.stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining("; "));
        
        log.warn("参数绑定失败: {}", message);
        
        ApiResponse<Void> response = ApiResponse.fail(ErrorCode.PARAM_INVALID, message);
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }
    
    /**
     * 处理约束违反异常 (Validated)
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolationException(
            ConstraintViolationException e) {
        
        Set<ConstraintViolation<?>> violations = e.getConstraintViolations();
        String message = violations.stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.joining("; "));
        
        log.warn("约束违反: {}", message);
        
        ApiResponse<Void> response = ApiResponse.fail(ErrorCode.PARAM_INVALID, message);
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }
    
    /**
     * 处理请求参数缺失异常
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingServletRequestParameterException(
            MissingServletRequestParameterException e) {
        
        String message = "缺少必需参数: " + e.getParameterName();
        log.warn(message);
        
        ApiResponse<Void> response = ApiResponse.fail(ErrorCode.PARAM_INVALID, message);
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }
    
    /**
     * 处理参数类型不匹配异常
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodArgumentTypeMismatchException(
            MethodArgumentTypeMismatchException e) {
        
        String message = String.format("参数类型错误: %s 应为 %s 类型", 
                e.getName(), e.getRequiredType() != null ? e.getRequiredType().getSimpleName() : "未知");
        log.warn(message);
        
        ApiResponse<Void> response = ApiResponse.fail(ErrorCode.PARAM_INVALID, message);
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }
    
    /**
     * 处理请求体解析异常
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleHttpMessageNotReadableException(
            HttpMessageNotReadableException e) {
        
        log.warn("请求体解析失败: {}", e.getMessage());
        
        ApiResponse<Void> response = ApiResponse.fail(ErrorCode.PARAM_INVALID, "请求体格式错误");
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }
    
    // ==================== HTTP错误处理 ====================
    
    /**
     * 处理404错误
     */
    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoHandlerFoundException(NoHandlerFoundException e) {
        log.warn("资源不存在: {} {}", e.getHttpMethod(), e.getRequestURL());
        
        ApiResponse<Void> response = ApiResponse.fail(ErrorCode.NOT_FOUND);
        return new ResponseEntity<>(response, HttpStatus.NOT_FOUND);
    }
    
    /**
     * 处理请求方法不支持异常
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleHttpRequestMethodNotSupportedException(
            HttpRequestMethodNotSupportedException e) {
        
        log.warn("请求方法不支持: {}", e.getMethod());
        
        ApiResponse<Void> response = ApiResponse.fail(ErrorCode.METHOD_NOT_ALLOWED, 
                "不支持 " + e.getMethod() + " 请求方法");
        return new ResponseEntity<>(response, HttpStatus.METHOD_NOT_ALLOWED);
    }
    
    // ==================== 通用异常处理 ====================
    
    /**
     * 处理其他所有未捕获异常
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception e) {
        log.error("系统异常: ", e);
        
        ApiResponse<Void> response = ApiResponse.fail(ErrorCode.INTERNAL_ERROR, 
                "系统内部错误，请稍后重试");
        return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
    }
    
    // ==================== 辅助方法 ====================
    
    /**
     * 将业务错误码映射到HTTP状态码
     */
    private HttpStatus mapToHttpStatus(ErrorCode errorCode) {
        int code = errorCode.getCode();
        if (code == 0) {
            return HttpStatus.OK;
        } else if (code >= 400 && code < 500) {
            return HttpStatus.valueOf(code);
        } else if (code >= 500 && code < 600) {
            return HttpStatus.valueOf(code);
        } else {
            // 业务错误码返回200，让前端根据业务码处理
            return HttpStatus.OK;
        }
    }
}
