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
 * 企业微信机器人告警发送器
 * <p>
 * 通过企业微信 Webhook 发送告警消息
 * </p>
 */
@Component
public class WeChatAlarmSender implements AlarmSender {

    private static final Logger logger = LoggerFactory.getLogger(WeChatAlarmSender.class);
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public WeChatAlarmSender(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    @Override
    public String getType() {
        return "WECHAT";
    }

    @Override
    public boolean send(AlarmChannel channel, AlarmTemplate template, Map<String, Object> params) {
        try {
            // 解析渠道配置
            WeChatConfig config = objectMapper.readValue(channel.getConfig(), WeChatConfig.class);

            // 构建消息内容
            String content = template != null && template.getContent() != null
                    ? replaceVariables(template.getContent(), params)
                    : buildDefaultContent(params);

            // 构建企业微信消息体
            String requestBody = buildMessageBody(content, params);

            // 发送请求
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(config.getWebhook()))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                Map<String, Object> result = objectMapper.readValue(response.body(), Map.class);
                if (Integer.valueOf(0).equals(result.get("errcode"))) {
                    logger.info("企业微信告警发送成功: taskName={}", params.get("taskName"));
                    return true;
                } else {
                    logger.error("企业微信告警发送失败: {}", result.get("errmsg"));
                    return false;
                }
            } else {
                logger.error("企业微信告警发送失败: HTTP {}", response.statusCode());
                return false;
            }

        } catch (Exception e) {
            logger.error("企业微信告警发送异常: {}", e.getMessage(), e);
            return false;
        }
    }

    private String buildMessageBody(String content, Map<String, Object> params) throws Exception {
        // 使用 Markdown 格式
        String level = (String) params.getOrDefault("level", "WARNING");
        String title = "【" + level + "】" + params.getOrDefault("taskName", "系统告警");

        Map<String, Object> markdown = Map.of(
                "content", "## " + title + "\n\n" + content);

        Map<String, Object> message = Map.of(
                "msgtype", "markdown",
                "markdown", markdown);

        return objectMapper.writeValueAsString(message);
    }

    private String buildDefaultContent(Map<String, Object> params) {
        StringBuilder sb = new StringBuilder();
        sb.append("> **任务名称:** ").append(params.getOrDefault("taskName", "-")).append("\n");
        sb.append("> **当前值:** ").append(params.getOrDefault("value", "-")).append("\n");
        sb.append("> **阈值:** ").append(params.getOrDefault("threshold", "-")).append("\n");
        sb.append("> **触发时间:** ").append(params.getOrDefault("time", "-")).append("\n");
        sb.append("> **触发类型:** ").append(params.getOrDefault("triggerType", "-")).append("\n");
        return sb.toString();
    }

    /**
     * 企业微信配置
     */
    public static class WeChatConfig {
        private String webhook;

        public String getWebhook() {
            return webhook;
        }

        public void setWebhook(String webhook) {
            this.webhook = webhook;
        }
    }
}
