package com.monitor.backend.filter;

import com.monitor.backend.constant.AuthConstants;
import com.monitor.backend.util.TraceIdUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Trace ID过滤器
 * <p>
 * 为每个HTTP请求生成唯一的Trace ID，用于链路追踪和日志关联。
 * 如果请求头中携带X-Trace-Id，则使用该值；否则自动生成。
 * </p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter {


    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {
        try {
            // 从请求头获取或生成Trace ID
            String traceId = request.getHeader(AuthConstants.TRACE_ID_HEADER);
            TraceIdUtils.initTrace(traceId);
            
            // 将Trace ID添加到响应头，便于前端调试
            response.setHeader(AuthConstants.TRACE_ID_HEADER, TraceIdUtils.getTraceId());
            
            filterChain.doFilter(request, response);
        } finally {
            // 清理MDC，防止线程池复用导致数据污染
            TraceIdUtils.clear();
        }
    }
}
