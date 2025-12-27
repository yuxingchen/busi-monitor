package com.monitor.backend.alarm.sender;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.monitor.backend.entity.AlarmChannel;
import com.monitor.backend.entity.AlarmTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;

/**
 * 钉钉机器人告警发送器
 * <p>
 * 通过钉钉 Webhook 发送告警消息
 * </p>
 */
@Component
public class DingTalkAlarmSender implements AlarmSender {

    private static final Logger logger = LoggerFactory.getLogger(DingTalkAlarmSender.class);
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public DingTalkAlarmSender(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    @Override
    public String getType() {
        return "DINGTALK";
    }

    @Override
    public boolean send(AlarmChannel channel, AlarmTemplate template, Map<String, Object> params) {
        try {
            // 解析渠道配置
            DingTalkConfig config = objectMapper.readValue(channel.getConfig(), DingTalkConfig.class);

            // 构建 Webhook URL（如有签名）
            String webhookUrl = buildWebhookUrl(config);

            // 构建消息内容
            String content = template != null && template.getContent() != null
                    ? replaceVariables(template.getContent(), params)
                    : buildDefaultContent(params);

            // 构建钉钉消息体
            String requestBody = buildMessageBody(content, params);

            // 发送请求
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(webhookUrl))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                Map<String, Object> result = objectMapper.readValue(response.body(), Map.class);
                if (Integer.valueOf(0).equals(result.get("errcode"))) {
                    logger.info("钉钉告警发送成功: taskName={}", params.get("taskName"));
                    return true;
                } else {
                    logger.error("钉钉告警发送失败: {}", result.get("errmsg"));
                    return false;
                }
            } else {
                logger.error("钉钉告警发送失败: HTTP {}", response.statusCode());
                return false;
            }

        } catch (Exception e) {
            logger.error("钉钉告警发送异常: {}", e.getMessage(), e);
            return false;
        }
    }

    private String buildWebhookUrl(DingTalkConfig config) throws Exception {
        String webhook = config.getWebhook();

        // 如果配置了签名密钥，添加签名参数
        if (config.getSecret() != null && !config.getSecret().isEmpty()) {
            long timestamp = System.currentTimeMillis();
            String sign = generateSign(timestamp, config.getSecret());
            webhook += "&timestamp=" + timestamp + "&sign=" + sign;
        }

        return webhook;
    }

    private String generateSign(long timestamp, String secret) throws Exception {
        String stringToSign = timestamp + "\n" + secret;
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] signData = mac.doFinal(stringToSign.getBytes(StandardCharsets.UTF_8));
        return URLEncoder.encode(Base64.getEncoder().encodeToString(signData), StandardCharsets.UTF_8);
    }

    private String buildMessageBody(String content, Map<String, Object> params) throws Exception {
        // 使用 Markdown 格式
        String level = (String) params.getOrDefault("level", "WARNING");
        String title = "【" + level + "】" + params.getOrDefault("taskName", "系统告警");

        Map<String, Object> markdown = Map.of(
                "title", title,
                "text", "## " + title + "\n\n" + content);

        Map<String, Object> message = Map.of(
                "msgtype", "markdown",
                "markdown", markdown);

        return objectMapper.writeValueAsString(message);
    }

    private String buildDefaultContent(Map<String, Object> params) {
        StringBuilder sb = new StringBuilder();
        sb.append("- **任务名称:** ").append(params.getOrDefault("taskName", "-")).append("\n");
        sb.append("- **当前值:** ").append(params.getOrDefault("value", "-")).append("\n");
        sb.append("- **阈值:** ").append(params.getOrDefault("threshold", "-")).append("\n");
        sb.append("- **触发时间:** ").append(params.getOrDefault("time", "-")).append("\n");
        sb.append("- **触发类型:** ").append(params.getOrDefault("triggerType", "-")).append("\n");
        return sb.toString();
    }

    /**
     * 钉钉配置
     */
    public static class DingTalkConfig {
        private String webhook;
        private String secret;

        public String getWebhook() {
            return webhook;
        }

        public void setWebhook(String webhook) {
            this.webhook = webhook;
        }

        public String getSecret() {
            return secret;
        }

        public void setSecret(String secret) {
            this.secret = secret;
        }
    }
}
