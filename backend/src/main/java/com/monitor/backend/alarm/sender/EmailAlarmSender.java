package com.monitor.backend.alarm.sender;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.monitor.backend.constant.BatchDefaults;
import com.monitor.backend.entity.AlarmChannel;
import com.monitor.backend.entity.AlarmTemplate;
import com.monitor.backend.enums.AlarmChannelType;
import com.monitor.backend.enums.AlarmContentType;
import com.monitor.backend.util.MarkdownUtils;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Properties;

/**
 * 邮件告警发送器
 * <p>
 * 通过 SMTP 发送告警邮件
 * </p>
 */
@Component
public class EmailAlarmSender implements AlarmSender {

    private static final Logger logger = LoggerFactory.getLogger(EmailAlarmSender.class);
    private final ObjectMapper objectMapper;

    public EmailAlarmSender(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public AlarmChannelType getType() {
        return AlarmChannelType.EMAIL;
    }

    @Override
    public boolean send(AlarmChannel channel, AlarmTemplate template, Map<String, Object> params) {
        try {
            // 解析渠道配置
            EmailConfig config = objectMapper.readValue(channel.getConfig(), EmailConfig.class);

            // 创建邮件发送器
            JavaMailSender mailSender = createMailSender(config);

            // 构建邮件内容
            String subject = template != null && template.getSubject() != null
                    ? replaceVariables(template.getSubject(), params)
                    : "【告警】" + params.getOrDefault("taskName", "系统告警");

            String content = template != null && template.getContent() != null
                    ? replaceVariables(template.getContent(), params)
                    : buildDefaultContent(params);

            // 根据内容格式决定是否进行 Markdown 转换
            String htmlContent;
            if (template != null && AlarmContentType.MARKDOWN.name().equals(template.getContentType())) {
                // Markdown 格式：转换为 HTML
                htmlContent = MarkdownUtils.toHtml(content);
            } else {
                // 纯文本格式：简单包装为 HTML
                htmlContent = wrapPlainTextAsHtml(content);
            }

            // 发送邮件
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(config.getUsername());
            helper.setTo(config.getRecipients().split(BatchDefaults.DEFAULT_SEPARATOR));
            helper.setSubject(subject);
            helper.setText(htmlContent, true);

            mailSender.send(message);
            logger.info("邮件告警发送成功: to={}, subject={}", config.getRecipients(), subject);
            return true;

        } catch (Exception e) {
            logger.error("邮件告警发送失败: {}", e.getMessage(), e);
            return false;
        }
    }

    private JavaMailSender createMailSender(EmailConfig config) {
        JavaMailSenderImpl mailSender = new JavaMailSenderImpl();
        mailSender.setHost(config.getHost());
        mailSender.setPort(config.getPort());
        mailSender.setUsername(config.getUsername());
        mailSender.setPassword(config.getPassword());

        Properties props = mailSender.getJavaMailProperties();
        props.put("mail.transport.protocol", "smtp");
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.timeout", "10000");
        props.put("mail.smtp.connectiontimeout", "10000");
        props.put("mail.smtp.writetimeout", "10000");

        // SSL 配置（465 端口使用 SSL，587 端口使用 STARTTLS）
        if (config.getPort() == 465) {
            props.put("mail.smtp.ssl.enable", "true");
            props.put("mail.smtp.ssl.protocols", "TLSv1.2 TLSv1.3");
            props.put("mail.smtp.ssl.trust", config.getHost());
            props.put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory");
            props.put("mail.smtp.socketFactory.port", String.valueOf(config.getPort()));
            props.put("mail.smtp.socketFactory.fallback", "false");
        } else {
            props.put("mail.smtp.starttls.enable", "true");
            props.put("mail.smtp.starttls.required", "true");
        }

        return mailSender;
    }

    private String buildDefaultContent(Map<String, Object> params) {
        StringBuilder sb = new StringBuilder();
        sb.append("<h3>告警通知</h3>");
        sb.append("<p><strong>任务名称:</strong> ").append(params.getOrDefault("taskName", "-")).append("</p>");
        sb.append("<p><strong>当前值:</strong> ").append(params.getOrDefault("value", "-")).append("</p>");
        sb.append("<p><strong>阈值:</strong> ").append(params.getOrDefault("threshold", "-")).append("</p>");
        sb.append("<p><strong>触发时间:</strong> ").append(params.getOrDefault("time", "-")).append("</p>");
        sb.append("<p><strong>触发类型:</strong> ").append(params.getOrDefault("triggerType", "-")).append("</p>");
        return sb.toString();
    }

    /**
     * 将纯文本包装为 HTML 格式
     */
    private String wrapPlainTextAsHtml(String plainText) {
        if (plainText == null || plainText.isEmpty()) {
            return "";
        }
        // 将换行符转换为 <br> 标签
        String htmlContent = plainText.replace("\n", "<br>\n");
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html><html><head><meta charset='UTF-8'>");
        sb.append("<style>");
        sb.append("body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; ");
        sb.append("line-height: 1.6; color: #333; max-width: 600px; margin: 0 auto; padding: 20px; }");
        sb.append("</style></head><body>");
        sb.append(htmlContent);
        sb.append("</body></html>");
        return sb.toString();
    }

    /**
     * 邮件配置
     */
    public static class EmailConfig {
        private String host;
        private int port = 465;
        private String username;
        private String password;
        private String recipients;

        public String getHost() {
            return host;
        }

        public void setHost(String host) {
            this.host = host;
        }

        public int getPort() {
            return port;
        }

        public void setPort(int port) {
            this.port = port;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public String getRecipients() {
            return recipients;
        }

        public void setRecipients(String recipients) {
            this.recipients = recipients;
        }
    }
}
