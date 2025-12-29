package com.monitor.backend.enums;

import lombok.Getter;

public enum AlarmChannelType {
    /**
     * 邮件通知
     */
    EMAIL,
    
    /**
     * 钉钉通知
     */
    DINGTALK,
    
    /**
     * 微信通知
     */
    WECHAT,
    
    /**
     * 短信通知
     */
    SMS,
    
    /**
     * 公告通知
     */
    ANNOUNCEMENT;
}
