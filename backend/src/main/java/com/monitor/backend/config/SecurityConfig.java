package com.monitor.backend.config;

import java.util.Arrays;
import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Spring Security 配置
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {
    
    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    
    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }
    
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // 启用CORS配置
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            
            // 禁用CSRF（使用JWT不需要CSRF保护）
            .csrf(AbstractHttpConfigurer::disable)
            
            // 禁用默认的表单登录和HTTP Basic认证
            .formLogin(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable)
            
            // 使用无状态Session（JWT不需要Session）
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            
            // 配置请求权限
            .authorizeHttpRequests(auth -> auth
                // 放行登录接口
                .requestMatchers("/api/auth/login").permitAll()
                // 放行WebSocket端点
                .requestMatchers("/api/ws/**").permitAll()
                // 放行错误页面（避免错误处理时循环403）
                .requestMatchers("/error").permitAll()
                // 放行Swagger/OpenAPI文档
                .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**", "/v3/api-docs.yaml").permitAll()
                // 放行静态资源（如果有）
                .requestMatchers("/", "/index.html", "/assets/**", "/*.js", "/*.css", "/*.ico").permitAll()
                // 其他请求需要认证
                .anyRequest().authenticated()
            )
            
            // 添加JWT过滤器
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        
        return http.build();
    }
    
    /**
     * CORS 配置源
     * 在 Security Filter 层面处理 CORS，确保 OPTIONS 预检请求不被拦截
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        // 允许所有来源
        configuration.setAllowedOriginPatterns(List.of("*"));
        // 允许的HTTP方法
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        // 允许所有请求头
        configuration.setAllowedHeaders(List.of("*"));
        // 允许的响应头
        configuration.setExposedHeaders(List.of("Authorization", "Content-Type"));
        // 允许携带凭证（cookies, authorization headers）
        configuration.setAllowCredentials(true);
        // 预检请求缓存时间
        configuration.setMaxAge(3600L);
        
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}

