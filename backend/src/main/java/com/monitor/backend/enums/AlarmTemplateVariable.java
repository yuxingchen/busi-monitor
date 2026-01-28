package com.monitor.backend.enums;

import com.monitor.backend.alarm.AlarmContext;

import java.util.function.Function;

/**
 * 告警模板变量枚举
 * 用于告警模板中的占位符替换
 */
public enum AlarmTemplateVariable {
    SERVER_NAME("${serverName}", "服务器名称", ctx -> ctx.getTaskName()),
    VALUE("${value}", "当前值", ctx -> formatDouble(ctx.getCurrentValue())),
    THRESHOLD("${threshold}", "阈值", ctx -> formatDouble(ctx.getThresholdValue())),
    TASK_NAME("${taskName}", "任务名称", ctx -> ctx.getTaskName()),
    TASK_ID("${taskId}", "任务ID", ctx -> ctx.getTaskId() != null ? ctx.getTaskId().toString() : null),
    OPERATOR("${operator}", "比较运算符", ctx -> ctx.getOperator()),
    TRIGGER_TYPE("${triggerType}", "触发类型", ctx -> ctx.getTriggerType()),
    LEVEL("${level}", "告警级别", ctx -> AlarmLevel.fromString(ctx.getLevel()).name());

    private final String placeholder;
    private final String description;
    private final Function<AlarmContext, String> valueExtractor;

    AlarmTemplateVariable(String placeholder, String description, Function<AlarmContext, String> valueExtractor) {
        this.placeholder = placeholder;
        this.description = description;
        this.valueExtractor = valueExtractor;
    }

    public String getPlaceholder() {
        return placeholder;
    }

    public String getDescription() {
        return description;
    }

    /**
     * 从上下文中提取值，如果为null则返回空字符串
     */
    public String getValue(AlarmContext context) {
        String value = valueExtractor.apply(context);
        return value != null ? value : "";
    }

    /**
     * 替换模板中的所有标准变量
     */
    public static String replaceAll(String template, AlarmContext context) {
        if (template == null) {
            return "";
        }
        String result = template;
        for (AlarmTemplateVariable var : values()) {
            result = result.replace(var.getPlaceholder(), var.getValue(context));
        }
        return result;
    }

    /**
     * 格式化 Double 值：如果不含小数部分则按整型输出
     */
    public static String formatDouble(Double value) {
        if (value == null) {
            return "";
        }
        // 检查是否为整数（没有小数部分）
        if (value == Math.floor(value) && !Double.isInfinite(value)) {
            return String.valueOf(value.longValue());
        }
        return String.format("%.2f", value);
    }
}
