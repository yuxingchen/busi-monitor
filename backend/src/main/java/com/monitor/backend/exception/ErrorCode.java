package com.monitor.backend.exception;

/**
 * 错误码枚举
 * <p>
 * 定义系统中所有的错误码，用于统一异常处理和响应。
 * 错误码规则：
 * - 0: 成功
 * - 400-499: 客户端错误（参数错误、认证失败等）
 * - 500-599: 服务端错误
 * - 1xxx: 业务相关错误
 * </p>
 *
 * @author monitor-system
 */
public enum ErrorCode {
    
    // ==================== 成功 ====================
    SUCCESS(0, "操作成功"),
    
    // ==================== 客户端错误 (4xx) ====================
    PARAM_INVALID(400, "参数无效"),
    UNAUTHORIZED(401, "未授权，请先登录"),
    FORBIDDEN(403, "权限不足"),
    NOT_FOUND(404, "资源不存在"),
    METHOD_NOT_ALLOWED(405, "请求方法不支持"),
    
    // ==================== 服务端错误 (5xx) ====================
    INTERNAL_ERROR(500, "系统内部错误"),
    SERVICE_UNAVAILABLE(503, "服务暂时不可用"),
    
    // ==================== 业务错误 (1xxx) ====================
    // 用户相关 10xx
    USER_NOT_FOUND(1001, "用户不存在"),
    USER_ALREADY_EXISTS(1002, "用户已存在"),
    PASSWORD_INCORRECT(1003, "密码错误"),
    USER_DISABLED(1004, "用户已被禁用"),
    
    // 数据源相关 11xx
    DATASOURCE_NOT_FOUND(1101, "数据源不存在"),
    DATASOURCE_CONNECTION_FAILED(1102, "数据源连接失败"),
    
    // 服务器相关 12xx
    SERVER_NOT_FOUND(1201, "服务器不存在"),
    SERVER_CONNECTION_FAILED(1202, "服务器连接失败"),
    SSH_EXECUTE_FAILED(1203, "SSH命令执行失败"),
    
    // 工作流相关 13xx
    WORKFLOW_NOT_FOUND(1301, "工作流不存在"),
    WORKFLOW_EXECUTE_FAILED(1302, "工作流执行失败"),
    WORKFLOW_STEP_INVALID(1303, "工作流步骤配置无效"),
    SQL_PARSE_ERROR(1304, "SQL解析错误"),
    
    // 告警相关 14xx
    ALARM_CHANNEL_NOT_FOUND(1401, "告警通道不存在"),
    ALARM_TEMPLATE_NOT_FOUND(1402, "告警模板不存在"),
    ALARM_SEND_FAILED(1403, "告警发送失败"),
    
    // 监控任务相关 15xx
    TASK_NOT_FOUND(1501, "监控任务不存在"),
    TASK_EXECUTE_FAILED(1502, "任务执行失败");
    
    private final int code;
    private final String message;
    
    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
    
    public int getCode() {
        return code;
    }
    
    public String getMessage() {
        return message;
    }
}
