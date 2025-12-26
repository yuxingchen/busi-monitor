package com.monitor.backend.alarm.sender;

import java.util.Map;

import com.monitor.backend.entity.AlarmChannel;
import com.monitor.backend.entity.AlarmTemplate;

/**
 * 告警发送器接口
 * <p>
 * 定义告警通知发送的统一接口，支持多种渠道类型的实现。
 * </p>
 */
public interface AlarmSender {

    /**
     * 获取发送器支持的渠道类型
     * 
     * @return 渠道类型，如 EMAIL, DINGTALK, SMS, ANNOUNCEMENT
     */
    String getType();

    /**
     * 发送告警通知
     * 
     * @param channel  告警渠道配置
     * @param template 告警模板（可为null，使用默认消息）
     * @param params   模板变量参数
     * @return 发送是否成功
     */
    boolean send(AlarmChannel channel, AlarmTemplate template, Map<String, Object> params);

    /**
     * 替换模板中的变量
     * 
     * @param template 模板内容
     * @param params   变量参数
     * @return 替换后的内容
     */
    default String replaceVariables(String template, Map<String, Object> params) {
        if (template == null || params == null) {
            return template;
        }
        String result = template;
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            String placeholder = "${" + entry.getKey() + "}";
            String value = entry.getValue() != null ? entry.getValue().toString() : "";
            result = result.replace(placeholder, value);
        }
        return result;
    }
}
