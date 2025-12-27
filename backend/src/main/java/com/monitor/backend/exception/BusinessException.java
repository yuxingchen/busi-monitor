package com.monitor.backend.exception;

/**
 * 业务异常基类
 * <p>
 * 所有业务异常均应继承此类，通过 {@link ErrorCode} 标识错误类型。
 * 该异常会被 {@link GlobalExceptionHandler} 统一捕获处理。
 * </p>
 *
 * @author monitor-system
 * @see ErrorCode
 * @see GlobalExceptionHandler
 */
public class BusinessException extends RuntimeException {
    
    private final ErrorCode errorCode;
    
    /**
     * 使用错误码构造业务异常
     *
     * @param errorCode 错误码
     */
    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }
    
    /**
     * 使用错误码和自定义消息构造业务异常
     *
     * @param errorCode 错误码
     * @param message   自定义消息（覆盖错误码默认消息）
     */
    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
    
    /**
     * 使用错误码和原因异常构造业务异常
     *
     * @param errorCode 错误码
     * @param cause     原因异常
     */
    public BusinessException(ErrorCode errorCode, Throwable cause) {
        super(errorCode.getMessage(), cause);
        this.errorCode = errorCode;
    }
    
    /**
     * 使用错误码、自定义消息和原因异常构造业务异常
     *
     * @param errorCode 错误码
     * @param message   自定义消息
     * @param cause     原因异常
     */
    public BusinessException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }
    
    public ErrorCode getErrorCode() {
        return errorCode;
    }
    
    public int getCode() {
        return errorCode.getCode();
    }
}
