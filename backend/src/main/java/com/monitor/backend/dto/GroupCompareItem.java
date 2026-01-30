package com.monitor.backend.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * 分组对比明细项
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GroupCompareItem {
    
    /** 分组键 (如："华东|产品A") */
    private String groupKey;
    
    /** 当前值 */
    private Double currentValue;
    
    /** 历史值 */
    private Double previousValue;
    
    /** 变化率(%) */
    private Double changeRate;
    
    /** 状态: NORMAL=正常, ALERT=触发告警, NEW=新增分组, MISSING=历史缺失 */
    private String status;
    
    /**
     * 构建比较项
     */
    public static GroupCompareItem of(String groupKey, Double current, Double previous, 
            Double changeRate, String status) {
        return new GroupCompareItem(groupKey, current, previous, changeRate, status);
    }
    
    /**
     * 判断是否触发告警
     */
    public boolean isAlert() {
        return "ALERT".equals(status);
    }
    
    /**
     * 格式化输出
     */
    public String format() {
        if (changeRate != null) {
            String sign = changeRate >= 0 ? "+" : "";
            return String.format("%s: %s%.1f%%", groupKey, sign, changeRate);
        }
        return String.format("%s: %s", groupKey, status);
    }
}
