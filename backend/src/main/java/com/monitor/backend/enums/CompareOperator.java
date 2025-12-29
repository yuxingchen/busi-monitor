package com.monitor.backend.enums;

import lombok.Getter;

/**
 * 比较操作符枚举
 */
@Getter
public enum CompareOperator {
    GREATER_THAN(">", "大于"),
    GREATER_THAN_OR_EQUAL(">=", "大于等于"),
    LESS_THAN("<", "小于"),
    LESS_THAN_OR_EQUAL("<=", "小于等于"),
    EQUAL("=", "等于");
    
    private final String symbol;
    private final String description;
    
    CompareOperator(String symbol, String description) {
        this.symbol = symbol;
        this.description = description;
    }

    /**
     * 执行比较运算
     */
    public boolean compare(double value, double threshold) {
        return switch (this) {
            case GREATER_THAN -> value > threshold;
            case GREATER_THAN_OR_EQUAL -> value >= threshold;
            case LESS_THAN -> value < threshold;
            case LESS_THAN_OR_EQUAL -> value <= threshold;
            case EQUAL -> Math.abs(value - threshold) < 0.0001;
        };
    }
    
    public static CompareOperator fromSymbol(String symbol) {
        for (CompareOperator op : values()) {
            if (op.symbol.equals(symbol)) {
                return op;
            }
        }
        throw new IllegalArgumentException("Unknown operator: " + symbol);
    }
}
