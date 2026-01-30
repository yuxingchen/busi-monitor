package com.monitor.backend.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.util.List;

/**
 * 同比环比对比结果
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CompareResult {
    
    /** 是否应该触发告警 */
    private boolean shouldAlert;
    
    /** 当前值 (简单对比时使用) */
    private Double currentValue;
    
    /** 历史值 (简单对比时使用) */
    private Double previousValue;
    
    /** 变化值 = currentValue - previousValue */
    private Double changeValue;
    
    /** 变化率(%) = (changeValue / previousValue) * 100 */
    private Double changeRate;
    
    /** 对比周期标签 (如："环比昨日"、"同比去年") */
    private String periodLabel;
    
    /** 分组对比明细 (分组对比时使用) */
    private List<GroupCompareItem> items;
    
    /** 触发告警的分组数 */
    private int alertCount;
    
    /**
     * 构建简单对比结果
     */
    public static CompareResult ofSimple(Double current, Double previous, 
            Double changeValue, Double changeRate, boolean shouldAlert, String periodLabel) {
        CompareResult result = new CompareResult();
        result.setCurrentValue(current);
        result.setPreviousValue(previous);
        result.setChangeValue(changeValue);
        result.setChangeRate(changeRate);
        result.setShouldAlert(shouldAlert);
        result.setPeriodLabel(periodLabel);
        return result;
    }
    
    /**
     * 构建分组对比结果
     */
    public static CompareResult ofGrouped(List<GroupCompareItem> items, 
            int alertCount, boolean shouldAlert, String periodLabel) {
        CompareResult result = new CompareResult();
        result.setItems(items);
        result.setAlertCount(alertCount);
        result.setShouldAlert(shouldAlert);
        result.setPeriodLabel(periodLabel);
        return result;
    }
}
