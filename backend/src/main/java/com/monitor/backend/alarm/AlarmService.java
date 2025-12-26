package com.monitor.backend.alarm;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.monitor.backend.alarm.sender.AlarmSender;
import com.monitor.backend.entity.AlarmActive;
import com.monitor.backend.entity.AlarmChannel;
import com.monitor.backend.entity.AlarmHistory;
import com.monitor.backend.entity.AlarmTemplate;
import com.monitor.backend.mapper.AlarmActiveMapper;
import com.monitor.backend.mapper.AlarmChannelMapper;
import com.monitor.backend.mapper.AlarmHistoryMapper;
import com.monitor.backend.mapper.AlarmTemplateMapper;

/**
 * 告警核心服务
 * <p>
 * 统一管理告警的触发、发送、抑制、升级和恢复
 * </p>
 */
@Service
public class AlarmService {

    private static final Logger logger = LoggerFactory.getLogger(AlarmService.class);

    /** 默认抑制时间（分钟） */
    private static final int DEFAULT_SUPPRESS_MINUTES = 30;

    private final List<AlarmSender> senders;
    private final AlarmActiveMapper activeMapper;
    private final AlarmHistoryMapper historyMapper;
    private final AlarmChannelMapper channelMapper;
    private final AlarmTemplateMapper templateMapper;
    private final AlarmWebSocketService webSocketService;

    public AlarmService(
            List<AlarmSender> senders,
            AlarmActiveMapper activeMapper,
            AlarmHistoryMapper historyMapper,
            AlarmChannelMapper channelMapper,
            AlarmTemplateMapper templateMapper,
            AlarmWebSocketService webSocketService) {
        this.senders = senders;
        this.activeMapper = activeMapper;
        this.historyMapper = historyMapper;
        this.channelMapper = channelMapper;
        this.templateMapper = templateMapper;
        this.webSocketService = webSocketService;
    }

    /**
     * 触发告警
     */
    public void triggerAlarm(AlarmContext context) {
        logger.info("告警触发: taskId={}, taskType={}, value={}, threshold={}",
                context.getTaskId(), context.getTaskType(),
                context.getCurrentValue(), context.getThresholdValue());

        // 1. 查找或创建活跃告警
        AlarmActive alarm = activeMapper.findByTaskAndType(context.getTaskId(), context.getTaskType());
        boolean isNewAlarm = (alarm == null);

        if (isNewAlarm) {
            alarm = createNewAlarm(context);
            activeMapper.insert(alarm);
            logger.info("创建新告警: id={}", alarm.getId());
        } else {
            // 更新已存在的告警
            alarm.setTriggerValue(context.getCurrentValue());
            alarm.setThresholdValue(context.getThresholdValue());
            alarm.setLastTriggerTime(LocalDateTime.now());
            alarm.setTriggerCount(alarm.getTriggerCount() + 1);
            alarm.setMessage(buildMessage(context));
            activeMapper.update(alarm);
            logger.info("更新告警: id={}, triggerCount={}", alarm.getId(), alarm.getTriggerCount());
        }

        // 2. 始终广播到前端（实时更新告警状态）
        webSocketService.broadcastAlarm(alarm);

        // 3. 检查是否需要抑制渠道通知（邮件/短信等）
        if (shouldSuppress(alarm)) {
            logger.info("告警渠道通知被抑制: id={}", alarm.getId());
            return;
        }

        // 4. 发送渠道通知
        sendNotifications(alarm, context);
    }

    /**
     * 恢复告警
     */
    public void resolveAlarm(Long taskId, String taskType) {
        AlarmActive alarm = activeMapper.findByTaskAndType(taskId, taskType);
        // 同时处理 FIRING 和 ACKNOWLEDGED 状态的告警
        if (alarm != null && !"RESOLVED".equals(alarm.getStatus())) {
            alarm.setStatus("RESOLVED");
            alarm.setResolveTime(LocalDateTime.now());
            activeMapper.update(alarm);

            // 广播恢复通知
            webSocketService.broadcastResolve(alarm.getId());
            logger.info("告警已恢复: id={}, previousStatus={}", alarm.getId(), alarm.getStatus());
        }
    }

    /**
     * 确认告警
     */
    public void acknowledgeAlarm(Long alarmId, String acknowledgeBy) {
        AlarmActive alarm = activeMapper.findById(alarmId);
        if (alarm != null && "FIRING".equals(alarm.getStatus())) {
            alarm.setStatus("ACKNOWLEDGED");
            alarm.setAcknowledgeBy(acknowledgeBy);
            alarm.setAcknowledgeTime(LocalDateTime.now());
            activeMapper.update(alarm);

            // 广播确认通知
            webSocketService.broadcastAcknowledge(alarmId, acknowledgeBy);
            logger.info("告警已确认: id={}, by={}", alarmId, acknowledgeBy);
        }
    }

    /**
     * 手动抑制告警
     */
    public void suppressAlarm(Long alarmId, int minutes) {
        AlarmActive alarm = activeMapper.findById(alarmId);
        if (alarm != null) {
            alarm.setStatus("SUPPRESSED");
            alarm.setSuppressedUntil(LocalDateTime.now().plusMinutes(minutes));
            activeMapper.update(alarm);
            logger.info("告警已抑制: id={}, until={}", alarmId, alarm.getSuppressedUntil());
        }
    }

    /**
     * 获取所有活跃告警
     */
    public List<AlarmActive> getActiveAlarms() {
        return activeMapper.findAllActive();
    }

    /**
     * 检查告警升级（定时任务，每分钟执行）
     */
    @Scheduled(fixedRate = 60000)
    public void checkEscalation() {
        // 查找超过30分钟未确认的告警
        List<AlarmActive> alarms = activeMapper.findNeedEscalation(30);
        for (AlarmActive alarm : alarms) {
            // 升级告警级别
            if ("WARNING".equals(alarm.getLevel())) {
                alarm.setLevel("CRITICAL");
                activeMapper.update(alarm);
                logger.warn("告警已升级: id={}, newLevel=CRITICAL", alarm.getId());

                // 重新发送通知
                webSocketService.broadcastAlarm(alarm);
            }
        }
    }

    // ========== 私有方法 ==========

    private AlarmActive createNewAlarm(AlarmContext context) {
        AlarmActive alarm = new AlarmActive();
        alarm.setTaskId(context.getTaskId());
        alarm.setTaskType(context.getTaskType());
        alarm.setTriggerType(context.getTriggerType());
        alarm.setTriggerValue(context.getCurrentValue());
        alarm.setThresholdValue(context.getThresholdValue());
        alarm.setFirstTriggerTime(LocalDateTime.now());
        alarm.setLastTriggerTime(LocalDateTime.now());
        alarm.setTriggerCount(1);
        alarm.setStatus("FIRING");
        alarm.setLevel(context.getLevel() != null ? context.getLevel() : "WARNING");
        alarm.setMessage(buildMessage(context));
        return alarm;
    }

    private String buildMessage(AlarmContext context) {
        // 使用指定的告警模板ID
        if (context.getAlarmTemplateId() != null) {
            AlarmTemplate template = templateMapper.findById(context.getAlarmTemplateId());
            if (template != null && template.getContent() != null) {
                return replaceVariables(template.getContent(), context);
            }
        }
        // 默认消息格式
        return String.format(
                "%s: 当前值 %.2f %s 阈值 %.2f",
                context.getTaskName() != null ? context.getTaskName() : "任务" + context.getTaskId(),
                context.getCurrentValue(),
                context.getOperator() != null ? context.getOperator() : ">",
                context.getThresholdValue());
    }

    /**
     * 替换模板中的变量
     */
    private String replaceVariables(String template, AlarmContext context) {
        if (template == null) {
            return "";
        }
        String result = template;
        // 替换标准变量
        result = result.replace("${serverName}", context.getTaskName() != null ? context.getTaskName() : "");
        result = result.replace("${value}",
                context.getCurrentValue() != null ? String.format("%.2f", context.getCurrentValue()) : "");
        result = result.replace("${threshold}",
                context.getThresholdValue() != null ? String.format("%.2f", context.getThresholdValue()) : "");
        result = result.replace("${taskName}", context.getTaskName() != null ? context.getTaskName() : "");
        result = result.replace("${taskId}", context.getTaskId() != null ? context.getTaskId().toString() : "");
        result = result.replace("${operator}", context.getOperator() != null ? context.getOperator() : ">");
        result = result.replace("${triggerType}", context.getTriggerType() != null ? context.getTriggerType() : "");
        result = result.replace("${level}", context.getLevel() != null ? context.getLevel() : "WARNING");

        // 替换额外参数
        if (context.getExtraParams() != null) {
            for (Map.Entry<String, Object> entry : context.getExtraParams().entrySet()) {
                String placeholder = "${" + entry.getKey() + "}";
                String value = entry.getValue() != null ? entry.getValue().toString() : "";
                result = result.replace(placeholder, value);
            }
        }
        return result;
    }

    private boolean shouldSuppress(AlarmActive alarm) {
        // 如果处于抑制状态且未过期
        if ("SUPPRESSED".equals(alarm.getStatus())) {
            if (alarm.getSuppressedUntil() != null && alarm.getSuppressedUntil().isAfter(LocalDateTime.now())) {
                return true;
            }
            // 抑制已过期，恢复为 FIRING
            alarm.setStatus("FIRING");
            activeMapper.update(alarm);
        }

        // 默认抑制逻辑：同一告警30分钟内只发送一次
        if (alarm.getTriggerCount() > 1) {
            LocalDateTime suppressUntil = alarm.getFirstTriggerTime().plusMinutes(DEFAULT_SUPPRESS_MINUTES);
            return LocalDateTime.now().isBefore(suppressUntil);
        }

        return false;
    }

    private void sendNotifications(AlarmActive alarm, AlarmContext context) {
        List<Long> channelIds = context.getChannelIds();
        if (channelIds == null || channelIds.isEmpty()) {
            // 使用所有启用的渠道
            List<AlarmChannel> channels = channelMapper.findAllActive();
            for (AlarmChannel channel : channels) {
                sendToChannel(alarm, channel, context);
            }
        } else {
            for (Long channelId : channelIds) {
                AlarmChannel channel = channelMapper.findById(channelId);
                if (channel != null && channel.getIsActive() == 1) {
                    sendToChannel(alarm, channel, context);
                }
            }
        }
    }

    private void sendToChannel(AlarmActive alarm, AlarmChannel channel, AlarmContext context) {
        // 查找发送器
        AlarmSender sender = findSender(channel.getType());
        if (sender == null) {
            logger.warn("未找到发送器: type={}", channel.getType());
            return;
        }

        // 从 context 获取告警模板（业务调用时必须传入模板ID）
        AlarmTemplate template = null;
        if (context.getAlarmTemplateId() != null) {
            template = templateMapper.findById(context.getAlarmTemplateId());
        }

        // 构建参数
        Map<String, Object> params = buildParams(alarm, context);

        // 发送通知
        boolean success = sender.send(channel, template, params);

        // 记录历史
        saveHistory(alarm, channel, success, context);
    }

    private AlarmSender findSender(String type) {
        for (AlarmSender sender : senders) {
            if (sender.getType().equalsIgnoreCase(type)) {
                return sender;
            }
        }
        return null;
    }

    private Map<String, Object> buildParams(AlarmActive alarm, AlarmContext context) {
        Map<String, Object> params = new HashMap<>();
        params.put("alarm", alarm);
        params.put("taskId", alarm.getTaskId());
        params.put("taskType", alarm.getTaskType());
        params.put("taskName", context.getTaskName());
        params.put("value", context.getCurrentValue());
        params.put("threshold", context.getThresholdValue());
        params.put("time", LocalDateTime.now().toString());
        params.put("triggerType", context.getTriggerType());
        params.put("level", alarm.getLevel());

        if (context.getExtraParams() != null) {
            params.putAll(context.getExtraParams());
        }

        return params;
    }

    private void saveHistory(AlarmActive alarm, AlarmChannel channel, boolean success, AlarmContext context) {
        AlarmHistory history = new AlarmHistory();
        history.setTaskId(alarm.getTaskId());
        history.setChannelId(channel.getId());
        history.setTriggerTime(LocalDateTime.now());
        history.setTriggerType(alarm.getTriggerType());
        history.setTriggerValue(alarm.getTriggerValue());
        history.setThresholdValue(alarm.getThresholdValue());
        history.setMessage(alarm.getMessage());
        history.setIsSuccess(success ? 1 : 0);

        historyMapper.insert(history);
    }
}
