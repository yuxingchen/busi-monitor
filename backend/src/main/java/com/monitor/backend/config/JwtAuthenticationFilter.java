package com.monitor.backend.config;

import com.monitor.backend.constant.AuthConstants;
import com.monitor.backend.service.JwtTokenService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

/**
 * JWT认证过滤器
 * 从请求Header中提取Token并验证
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    
    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
    
    private final JwtTokenService jwtTokenService;
    
    public JwtAuthenticationFilter(JwtTokenService jwtTokenService) {
        this.jwtTokenService = jwtTokenService;
    }
    
    @Override
    protected void doFilterInternal(HttpServletRequest request, 
                                    HttpServletResponse response, 
                                    FilterChain filterChain) throws ServletException, IOException {
        
        String requestUri = request.getRequestURI();
        
        // 优先从 X-Auth-Token 获取（避免 Nginx auth_basic 拦截 Authorization 头）
        // 如果没有，则尝试从标准 Authorization 头获取
        String token = request.getHeader(AuthConstants.X_AUTH_TOKEN_HEADER);
        String authHeader = request.getHeader(AuthConstants.AUTHORIZATION_HEADER);
        
        // 调试日志
        log.debug("JWT Filter - URI: {}, Method: {}, X-Auth-Token present: {}, Authorization present: {}", 
                requestUri, request.getMethod(), token != null, authHeader != null);
        
        // 如果 X-Auth-Token 为空，尝试从 Authorization 头提取
        if (token == null && AuthConstants.isBearerToken(authHeader)) {
            token = AuthConstants.extractToken(authHeader);
        }
        
        if (token != null && !token.isEmpty()) {
            log.debug("JWT Filter - Token extracted, length: {}", token.length());
            
            String username = jwtTokenService.validateTokenAndGetUsername(token);
            
            if (username != null) {
                String role = jwtTokenService.getRoleFromToken(token);
                SimpleGrantedAuthority authority = new SimpleGrantedAuthority("ROLE_" + (role != null ? role : "USER"));
                
                UsernamePasswordAuthenticationToken authentication = 
                    new UsernamePasswordAuthenticationToken(username, null, Collections.singletonList(authority));
                
                SecurityContextHolder.getContext().setAuthentication(authentication);
                log.debug("JWT Filter - Authentication set for user: {}, role: {}", username, role);
            } else {
                log.warn("JWT Filter - Token validation failed for URI: {}", requestUri);
            }
        }
        
        filterChain.doFilter(request, response);
    }
}

