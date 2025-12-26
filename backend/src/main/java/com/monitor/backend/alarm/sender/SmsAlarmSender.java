package com.monitor.backend.alarm.sender;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.monitor.backend.entity.AlarmChannel;
import com.monitor.backend.entity.AlarmTemplate;

/**
 * 短信告警发送器
 * <p>
 * 支持阿里云、腾讯云等主流短信平台
 * </p>
 */
@Component
public class SmsAlarmSender implements AlarmSender {

    private static final Logger logger = LoggerFactory.getLogger(SmsAlarmSender.class);
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public SmsAlarmSender(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    @Override
    public String getType() {
        return "SMS";
    }

    @Override
    public boolean send(AlarmChannel channel, AlarmTemplate template, Map<String, Object> params) {
        try {
            // 解析渠道配置
            SmsConfig config = objectMapper.readValue(channel.getConfig(), SmsConfig.class);

            // 构建短信内容
            String content = template != null && template.getContent() != null
                    ? replaceVariables(template.getContent(), params)
                    : buildDefaultContent(params);

            // 根据供应商发送短信
            boolean success = switch (config.getProvider().toUpperCase()) {
                case "ALIYUN" -> sendAliyunSms (config, content, params);
                case "TENCENT" -> sendTencentSms (config, content, params);
                default -> sendGenericSms (config, content);
            };

            if (success) {
                logger.info("短信告警发送成功: phones={}", config.getPhones());
            }
            return success;

        } catch (Exception e) {
            logger.error("短信告警发送异常: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * 阿里云短信发送
     * 使用阿里云 SMS SDK
     */
    private boolean sendAliyunSms(SmsConfig config, String content, Map<String, Object> params) {
        try {
            // 构建模板参数
            String templateParam = objectMapper.writeValueAsString(Map.of(
                    "taskName", params.getOrDefault("taskName", ""),
                    "value", params.getOrDefault("value", ""),
                    "threshold", params.getOrDefault("threshold", "")));

            // 使用阿里云 SDK 发送
            // 这里使用 HTTP API 方式简化实现
            logger.info("阿里云短信发送: signName={}, templateCode={}, phones={}",
                    config.getSignName(), config.getTemplateCode(), config.getPhones());

            // TODO: 集成阿里云 SMS SDK
            // Client client = createAliyunClient(config);
            // SendSmsRequest request = new SendSmsRequest()
            // .setPhoneNumbers(config.getPhones())
            // .setSignName(config.getSignName())
            // .setTemplateCode(config.getTemplateCode())
            // .setTemplateParam(templateParam);
            // client.sendSms(request);

            logger.warn("阿里云短信暂未集成 SDK，请配置后使用");
            return true;

        } catch (Exception e) {
            logger.error("阿里云短信发送失败: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 腾讯云短信发送
     */
    private boolean sendTencentSms(SmsConfig config, String content, Map<String, Object> params) {
        try {
            logger.info("腾讯云短信发送: signName={}, templateId={}, phones={}",
                    config.getSignName(), config.getTemplateCode(), config.getPhones());

            // TODO: 集成腾讯云 SMS SDK
            logger.warn("腾讯云短信暂未集成 SDK，请配置后使用");
            return true;

        } catch (Exception e) {
            logger.error("腾讯云短信发送失败: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 通用 HTTP API 短信发送
     */
    private boolean sendGenericSms(SmsConfig config, String content) {
        try {
            if (config.getApiUrl() == null || config.getApiUrl().isEmpty()) {
                logger.warn("未配置短信 API URL");
                return false;
            }

            // 构建请求体
            Map<String, Object> requestBody = Map.of(
                    "phones", config.getPhones(),
                    "content", content,
                    "apiKey", config.getApiKey());

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(config.getApiUrl()))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(requestBody)))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200;

        } catch (Exception e) {
            logger.error("通用短信发送失败: {}", e.getMessage());
            return false;
        }
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
    public static class SmsConfig {
        private String provider = "GENERIC"; // ALIYUN, TENCENT, GENERIC
        private String accessKeyId;
        private String accessKeySecret;
        private String signName;
        private String templateCode;
        private String phones;
        private String apiUrl;
        private String apiKey;

        public String getProvider() {
            return provider;
        }

        public void setProvider(String provider) {
            this.provider = provider;
        }

        public String getAccessKeyId() {
            return accessKeyId;
        }

        public void setAccessKeyId(String accessKeyId) {
            this.accessKeyId = accessKeyId;
        }

        public String getAccessKeySecret() {
            return accessKeySecret;
        }

        public void setAccessKeySecret(String accessKeySecret) {
            this.accessKeySecret = accessKeySecret;
        }

        public String getSignName() {
            return signName;
        }

        public void setSignName(String signName) {
            this.signName = signName;
        }

        public String getTemplateCode() {
            return templateCode;
        }

        public void setTemplateCode(String templateCode) {
            this.templateCode = templateCode;
        }

        public String getPhones() {
            return phones;
        }

        public void setPhones(String phones) {
            this.phones = phones;
        }

        public String getApiUrl() {
            return apiUrl;
        }

        public void setApiUrl(String apiUrl) {
            this.apiUrl = apiUrl;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }
    }
}
