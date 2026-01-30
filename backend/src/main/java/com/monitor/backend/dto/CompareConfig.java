package com.monitor.backend.dto;

import lombok.Data;
import java.util.List;

/**
 * 同比环比对比配置
 */
@Data
public class CompareConfig {
    
    /** 是否启用同比环比 */
    private Boolean enabled;
    
    /** 对比类型: MOM=环比, YOY=同比 */
    private String compareType;
    
    /** 对比周期数 (如：1表示对比1天前/1周前) */
    private Integer periodCount;
    
    /** 周期单位: DAY/WEEK/MONTH */
    private String periodUnit;
    
    /** 对比模式: AUTO=自动识别, SIMPLE=简单对比, GROUPED=分组对比 */
    private String compareMode;
    
    /** 对比的数值字段 (DATASET时使用) */
    private String compareField;
    
    /** 聚合方式: SUM/COUNT/AVG/MAX/MIN */
    private String aggregateMethod;
    
    /** 分组键字段列表 (分组对比时使用) */
    private List<String> groupByFields;
    
    /** 变化计算类型: RATE=变化率(%), VALUE=变化值 */
    private String changeType;
    
    /** 变化比较操作符: >, >=, <, <=, = */
    private String changeOperator;
    
    /** 变化阈值 (变化率时为百分比) */
    private Double changeThreshold;
    
    /** 是否使用绝对值 (上涨下跌都触发) */
    private Boolean useAbsoluteValue;
    
    /** 分组告警模式: ANY=任一触发, ALL=全部触发 */
    private String alertMode;
    
    /** 最少触发分组数 (alertMode=ANY时使用) */
    private Integer minAlertCount;
    
    /** 历史数据缺失处理: SKIP=跳过, ALERT=触发告警 */
    private String handleMissing;
    
    /** 新增分组处理: SKIP=跳过, ALERT=触发告警 */
    private String handleNew;
    
    // ===== 默认值方法 =====
    
    public Integer getPeriodCount() {
        return periodCount != null && periodCount > 0 ? periodCount : 1;
    }
    
    public String getPeriodUnit() {
        return periodUnit != null ? periodUnit : "DAY";
    }
    
    public String getCompareMode() {
        return compareMode != null ? compareMode : "AUTO";
    }
    
    public String getChangeType() {
        return changeType != null ? changeType : "RATE";
    }
    
    public String getChangeOperator() {
        return changeOperator != null ? changeOperator : ">";
    }
    
    public Double getChangeThreshold() {
        return changeThreshold != null ? changeThreshold : 10.0;
    }
    
    public Boolean getUseAbsoluteValue() {
        return useAbsoluteValue != null ? useAbsoluteValue : true;
    }
    
    public String getAlertMode() {
        return alertMode != null ? alertMode : "ANY";
    }
    
    public Integer getMinAlertCount() {
        return minAlertCount != null && minAlertCount > 0 ? minAlertCount : 1;
    }
    
    public String getHandleMissing() {
        return handleMissing != null ? handleMissing : "SKIP";
    }
    
    public String getHandleNew() {
        return handleNew != null ? handleNew : "SKIP";
    }
}
