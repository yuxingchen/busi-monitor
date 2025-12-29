package com.monitor.backend.alarm.sender;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.monitor.backend.entity.AlarmChannel;
import com.monitor.backend.entity.AlarmTemplate;
import com.monitor.backend.enums.AlarmChannelType;
import com.monitor.backend.service.TransmitEncryptionService;
import com.monitor.backend.util.DateTimeUtils;
import com.monitor.backend.util.MarkdownUtils;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springdoc.core.service.GenericParameterService;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 短信告警发送器
 * <p>
 * 支持自定义 HTTP API 接口调用，支持占位符替换和签名计算
 * </p>
 */
@Component
public class SmsAlarmSender implements AlarmSender {

    private static final Logger logger = LoggerFactory.getLogger(SmsAlarmSender.class);
    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final TransmitEncryptionService encryptionService;
    private final GenericParameterService parameterBuilder;

    public SmsAlarmSender(ObjectMapper objectMapper, TransmitEncryptionService encryptionService, GenericParameterService parameterBuilder) {
        this.objectMapper = objectMapper;
        this.encryptionService = encryptionService;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.parameterBuilder = parameterBuilder;
    }

    @Override
    public AlarmChannelType getType() {
        return AlarmChannelType.SMS;
    }

    @Override
    public boolean send(AlarmChannel channel, AlarmTemplate template, Map<String, Object> params) {
        try {
            // 解析渠道配置
            SmsConfig config = objectMapper.readValue(channel.getConfig(), SmsConfig.class);

            // 构建短信内容
            String rawContent = template != null && template.getContent() != null
                    ? replaceVariables(template.getContent(), params)
                    : buildDefaultContent(params);

            // 清除 Markdown 格式
            String content = MarkdownUtils.stripMarkdown(rawContent);

            // 发送短信
            return sendSms(config, content);

        } catch (Exception e) {
            logger.error("短信告警发送异常: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * 发送短信
     */
    private boolean sendSms(SmsConfig config, String content) {
        try {
            if (config.getApiUrl() == null || config.getApiUrl().isEmpty()) {
                logger.warn("未配置短信 API URL");
                return false;
            }

            // 生成请求参数
            String uuid = UUID.randomUUID().toString().replace("-", "");
            String datetime = DateTimeUtils.now().format(DATETIME_FORMATTER);
            String encodedContent = URLEncoder.encode(content, StandardCharsets.UTF_8);
            Map<String, String> params = new HashMap<>();
            params.put("content", content);
            String encodeParams = URLEncoder.encode(objectMapper.writeValueAsString(params), StandardCharsets.UTF_8);

            // 解密 signKey
            String signKey = "";
            if (config.getSignKey() != null && !config.getSignKey().isEmpty()) {
                try {
                    signKey = encryptionService.decrypt(config.getSignKey());
                } catch (Exception e) {
                    logger.warn("signKey 解密失败，使用原值: {}", e.getMessage());
                    signKey = config.getSignKey();
                }
            }

            // 构建占位符映射
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("uuid", uuid);
            placeholders.put("phones", config.getPhones());
            placeholders.put("datetime", datetime);
            placeholders.put("content", content);
            placeholders.put("encodeContent", encodedContent);
            placeholders.put("encodeParam", encodeParams);
            placeholders.put("signKey", signKey);

            // 计算签名
            String sign = calculateSign(config, placeholders);
            placeholders.put("sign", sign);

            // 解析并替换参数模板
            String requestBody = resolveTemplate(config.getParamsTemplate(), placeholders);

            logger.info("短信发送请求: url={}, phones={}", config.getApiUrl(), config.getPhones());
            logger.debug("请求体: {}", requestBody);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(config.getApiUrl()))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            logger.info("短信发送响应: status={}, body={}", response.statusCode(), response.body());
            return response.statusCode() == 200;

        } catch (Exception e) {
            logger.error("短信发送失败: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * 计算签名
     */
    private String calculateSign(SmsConfig config, Map<String, String> placeholders) {
        if (config.getSignMethod() == null || "NONE".equalsIgnoreCase(config.getSignMethod())) {
            return "";
        }
        if (StringUtils.isEmpty(config.getSignFields())) {
            return "";
        }
        List<String> signFields = List.of(config.getSignFields().split(","));
        if (signFields.isEmpty()) {
            return "";
        }

        // 按 signFields 顺序拼接字段值
        StringBuilder signBuilder = new StringBuilder();
        for (String field : signFields) {
            String value = placeholders.getOrDefault(field, "");
            signBuilder.append(value);
        }

        String signString = signBuilder.toString();
        logger.debug("签名原串: {}", signString);

        try {
            return switch (config.getSignMethod().toUpperCase()) {
                case "MD5" -> md5(signString);
                case "SHA256" -> sha256(signString);
                default -> signString;
            };
        } catch (Exception e) {
            logger.error("签名计算失败: {}", e.getMessage());
            return "";
        }
    }

    /**
     * 解析模板，替换占位符
     */
    private String resolveTemplate(String template, Map<String, String> placeholders) {
        if (template == null || template.isEmpty()) {
            return "{}";
        }

        String result = template;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            String placeholder = "${" + entry.getKey() + "}";
            String value = entry.getValue() != null ? entry.getValue() : "";
            result = result.replace(placeholder, value);
        }
        return result;
    }

    /**
     * MD5 加密
     */
    private String md5(String input) throws Exception {
        MessageDigest md = MessageDigest.getInstance("MD5");
        byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
        return bytesToHex(digest);
    }

    /**
     * SHA256 加密
     */
    private String sha256(String input) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
        return bytesToHex(digest);
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private String buildDefaultContent(Map<String, Object> params) {
        return String.format(
                "【告警】%s: 当前值%s, 阈值%s, 触发时间%s",
                params.getOrDefault("taskName", "系统告警"),
                params.getOrDefault("value", "-"),
                params.getOrDefault("threshold", "-"),
                params.getOrDefault("time", "-"));
    }

    /**
     * 短信配置
     */
    @Setter
    @Getter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SmsConfig {
        private String apiUrl;
        private String phones;
        private String paramsTemplate;
        private String signMethod;  // NONE, MD5, SHA256
        private String signKey;     // 加密存储
        private String signFields;  // 签名字段列表（按顺序）

    }
}
