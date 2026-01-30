package com.monitor.backend.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.time.LocalDateTime;

/**
 * 对比周期单位
 */
@Getter
@RequiredArgsConstructor
public enum PeriodUnit {
    DAY("DAY", "日") {
        @Override
        public LocalDateTime subtract(LocalDateTime time, int count) {
            return time.minusDays(count);
        }
        @Override
        public String getShortLabel() { return "日"; }
    },
    WEEK("WEEK", "周") {
        @Override
        public LocalDateTime subtract(LocalDateTime time, int count) {
            return time.minusWeeks(count);
        }
        @Override
        public String getShortLabel() { return "周"; }
    },
    MONTH("MONTH", "月") {
        @Override
        public LocalDateTime subtract(LocalDateTime time, int count) {
            return time.minusMonths(count);
        }
        @Override
        public String getShortLabel() { return "月"; }
    };
    
    private final String code;
    private final String label;
    
    public abstract LocalDateTime subtract(LocalDateTime time, int count);
    public abstract String getShortLabel();
    
    public static PeriodUnit fromCode(String code) {
        if (code == null) return DAY;
        for (PeriodUnit unit : values()) {
            if (unit.code.equalsIgnoreCase(code)) {
                return unit;
            }
        }
        return DAY;
    }
}
