package com.monitor.backend.util;

import org.slf4j.MDC;

import java.util.UUID;

/**
 * Trace ID工具类
 * <p>
 * 用于生成和管理链路追踪ID，便于日志关联和问题排查。
 * </p>
 */
public final class TraceIdUtils {

    public static final String TRACE_ID_KEY = "traceId";
    public static final String SPAN_ID_KEY = "spanId";

    private TraceIdUtils() {
    }

    /**
     * 生成Trace ID
     */
    public static String generateTraceId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    /**
     * 生成Span ID
     */
    public static String generateSpanId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    /**
     * 获取当前线程的Trace ID
     */
    public static String getTraceId() {
        return MDC.get(TRACE_ID_KEY);
    }

    /**
     * 设置Trace ID到MDC
     */
    public static void setTraceId(String traceId) {
        MDC.put(TRACE_ID_KEY, traceId);
    }

    /**
     * 设置Span ID到MDC
     */
    public static void setSpanId(String spanId) {
        MDC.put(SPAN_ID_KEY, spanId);
    }

    /**
     * 清除MDC中的Trace信息
     */
    public static void clear() {
        MDC.remove(TRACE_ID_KEY);
        MDC.remove(SPAN_ID_KEY);
    }

    /**
     * 初始化Trace上下文
     */
    public static void initTrace() {
        setTraceId(generateTraceId());
        setSpanId(generateSpanId());
    }

    /**
     * 初始化Trace上下文（使用指定的Trace ID）
     */
    public static void initTrace(String traceId) {
        setTraceId(traceId != null ? traceId : generateTraceId());
        setSpanId(generateSpanId());
    }
}
