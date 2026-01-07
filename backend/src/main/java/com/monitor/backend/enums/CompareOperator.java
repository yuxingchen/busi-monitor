package com.monitor.backend.enums;

import lombok.Getter;

/**
 * 比较操作符枚举
 * 支持数值比较和字符串比较
 */
@Getter
public enum CompareOperator {
    GREATER_THAN(">", "大于"),
    GREATER_THAN_OR_EQUAL(">=", "大于等于"),
    LESS_THAN("<", "小于"),
    LESS_THAN_OR_EQUAL("<=", "小于等于"),
    EQUAL("=", "等于"),
    NOT_EQUAL("!=", "不等于"),
    CONTAINS("CONTAINS", "包含"),
    NOT_CONTAINS("NOT_CONTAINS", "不包含");
    
    private final String symbol;
    private final String description;
    
    CompareOperator(String symbol, String description) {
        this.symbol = symbol;
        this.description = description;
    }

    /**
     * 执行数值比较运算
     */
    public boolean compare(double value, double threshold) {
        return switch (this) {
            case GREATER_THAN -> value > threshold;
            case GREATER_THAN_OR_EQUAL -> value >= threshold;
            case LESS_THAN -> value < threshold;
            case LESS_THAN_OR_EQUAL -> value <= threshold;
            case EQUAL -> Math.abs(value - threshold) < 0.0001;
            case NOT_EQUAL -> Math.abs(value - threshold) >= 0.0001;
            case CONTAINS, NOT_CONTAINS -> false; // 字符串操作符不支持数值比较
        };
    }

    /**
     * 执行字符串比较运算
     */
    public boolean compareString(String value, String threshold) {
        if (value == null) {
            return false;
        }
        return switch (this) {
            case EQUAL -> threshold != null && value.equals(threshold);
            case NOT_EQUAL -> threshold == null || !value.equals(threshold);
            case CONTAINS -> threshold != null && value.contains(threshold);
            case NOT_CONTAINS -> threshold == null || !value.contains(threshold);
            case GREATER_THAN, GREATER_THAN_OR_EQUAL, LESS_THAN, LESS_THAN_OR_EQUAL -> {
                // 尝试数值比较
                try {
                    double v = Double.parseDouble(value);
                    double t = Double.parseDouble(threshold);
                    yield compare(v, t);
                } catch (NumberFormatException e) {
                    yield false;
                }
            }
        };
    }

    /**
     * 智能比较：自动判断数值或字符串比较
     */
    public boolean smartCompare(Object value, Object threshold, String thresholdStr) {
        if (value == null) {
            return false;
        }

        // 尝试数值比较
        Double numValue = toDouble(value);
        Double numThreshold = threshold instanceof Number ? ((Number) threshold).doubleValue() : null;
        
        if (numValue != null && numThreshold != null) {
            return compare(numValue, numThreshold);
        }

        // 回退到字符串比较
        String strValue = value.toString();
        String strThreshold = thresholdStr != null ? thresholdStr : (threshold != null ? threshold.toString() : null);
        return compareString(strValue, strThreshold);
    }

    private static Double toDouble(Object value) {
        if (value == null) return null;
        if (value instanceof Number) return ((Number) value).doubleValue();
        try {
            return Double.parseDouble(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }
    
    public static CompareOperator fromSymbol(String symbol) {
        if (symbol == null) {
            throw new IllegalArgumentException("Operator symbol cannot be null");
        }
        for (CompareOperator op : values()) {
            if (op.symbol.equalsIgnoreCase(symbol)) {
                return op;
            }
        }
        throw new IllegalArgumentException("Unknown operator: " + symbol);
    }
}
