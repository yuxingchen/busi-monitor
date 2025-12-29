package com.monitor.backend.alarm.sender;

import com.monitor.backend.alarm.AlarmWebSocketService;
import com.monitor.backend.entity.AlarmActive;
import com.monitor.backend.entity.AlarmChannel;
import com.monitor.backend.entity.AlarmTemplate;
import com.monitor.backend.enums.AlarmChannelType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 系统公告告警发送器
 * <p>
 * 通过 WebSocket 向前端实时推送告警公告
 * </p>
 */
@Component
public class AnnouncementAlarmSender implements AlarmSender {

    private static final Logger logger = LoggerFactory.getLogger(AnnouncementAlarmSender.class);

    private final AlarmWebSocketService webSocketService;

    public AnnouncementAlarmSender(AlarmWebSocketService webSocketService) {
        this.webSocketService = webSocketService;
    }

    @Override
    public AlarmChannelType getType() {
        return AlarmChannelType.ANNOUNCEMENT;
    }

    @Override
    public boolean send(AlarmChannel channel, AlarmTemplate template, Map<String, Object> params) {
        try {
            // 从参数中获取活跃告警对象
            AlarmActive alarm = (AlarmActive) params.get("alarm");

            if (alarm == null) {
                // 如果没有传入告警对象，构建一个临时的
                alarm = new AlarmActive();
                alarm.setTaskId((Long) params.get("taskId"));
                alarm.setTaskType((String) params.get("taskType"));
                alarm.setLevel((String) params.getOrDefault("level", "WARNING"));
                alarm.setTriggerValue((Double) params.get("value"));
                alarm.setThresholdValue((Double) params.get("threshold"));

                // 构建消息
                String message = template != null && template.getContent() != null
                        ? replaceVariables(template.getContent(), params)
                        : buildDefaultMessage(params);
                alarm.setMessage(message);
            }

            // 注意：不在此处广播，由 AlarmService.triggerAlarm 统一广播，避免重复通知
            logger.info("系统公告通道准备就绪: taskName={}", params.get("taskName"));
            return true;

        } catch (Exception e) {
            logger.error("系统公告发送失败: {}", e.getMessage(), e);
            return false;
        }
    }

    private String buildDefaultMessage(Map<String, Object> params) {
        return String.format(
                "%s: 当前值 %s，超过阈值 %s",
                params.getOrDefault("taskName", "系统告警"),
                params.getOrDefault("value", "-"),
                params.getOrDefault("threshold", "-"));
    }
}
