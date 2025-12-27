package com.monitor.backend.alarm;

import com.monitor.backend.entity.AlarmActive;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 告警 WebSocket 推送服务
 * <p>
 * 向前端实时推送告警通知
 * </p>
 */
@Service
public class AlarmWebSocketService {

    private static final Logger logger = LoggerFactory.getLogger(AlarmWebSocketService.class);

    private final SimpMessagingTemplate messagingTemplate;

    public AlarmWebSocketService(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    /**
     * 广播新告警
     */
    public void broadcastAlarm(AlarmActive alarm) {
        try {
            AlarmNotification notification = new AlarmNotification();
            notification.setType("FIRING");
            notification.setId(alarm.getId());
            notification.setTaskId(alarm.getTaskId());
            notification.setTaskType(alarm.getTaskType());
            notification.setLevel(alarm.getLevel());
            notification.setMessage(alarm.getMessage());
            notification.setTriggerValue(alarm.getTriggerValue());
            notification.setThresholdValue(alarm.getThresholdValue());
            notification.setTime(LocalDateTime.now());

            messagingTemplate.convertAndSend("/topic/alarm", notification);
            logger.info("告警广播成功: alarmId={}, level={}", alarm.getId(), alarm.getLevel());
        } catch (Exception e) {
            logger.error("告警广播失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 广播告警恢复通知
     */
    public void broadcastResolve(Long alarmId) {
        try {
            Map<String, Object> payload = Map.of(
                    "type", "RESOLVED",
                    "alarmId", alarmId,
                    "time", LocalDateTime.now().toString());
            messagingTemplate.convertAndSend("/topic/alarm", payload);
            logger.info("告警恢复广播成功: alarmId={}", alarmId);
        } catch (Exception e) {
            logger.error("告警恢复广播失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 广播告警确认通知
     */
    public void broadcastAcknowledge(Long alarmId, String acknowledgeBy) {
        try {
            Map<String, Object> payload = Map.of(
                    "type", "ACKNOWLEDGED",
                    "alarmId", alarmId,
                    "acknowledgeBy", acknowledgeBy,
                    "time", LocalDateTime.now().toString());
            messagingTemplate.convertAndSend("/topic/alarm", payload);
            logger.info("告警确认广播成功: alarmId={}, by={}", alarmId, acknowledgeBy);
        } catch (Exception e) {
            logger.error("告警确认广播失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 告警通知数据结构
     */
    public static class AlarmNotification {
        private String type;
        private Long id;
        private Long taskId;
        private String taskType;
        private String level;
        private String message;
        private Double triggerValue;
        private Double thresholdValue;
        private LocalDateTime time;

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public Long getTaskId() {
            return taskId;
        }

        public void setTaskId(Long taskId) {
            this.taskId = taskId;
        }

        public String getTaskType() {
            return taskType;
        }

        public void setTaskType(String taskType) {
            this.taskType = taskType;
        }

        public String getLevel() {
            return level;
        }

        public void setLevel(String level) {
            this.level = level;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        public Double getTriggerValue() {
            return triggerValue;
        }

        public void setTriggerValue(Double triggerValue) {
            this.triggerValue = triggerValue;
        }

        public Double getThresholdValue() {
            return thresholdValue;
        }

        public void setThresholdValue(Double thresholdValue) {
            this.thresholdValue = thresholdValue;
        }

        public LocalDateTime getTime() {
            return time;
        }

        public void setTime(LocalDateTime time) {
            this.time = time;
        }
    }
}
