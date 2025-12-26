package com.monitor.backend.dto;

import java.util.List;
import java.util.Map;

/**
 * 图表配置 DTO
 * <p>
 * 用于解析 MonitorTask.chartConfig JSON 字段
 * </p>
 */
public class ChartConfigDto {
    
    /** 图表类型: bar, line, table 等 */
    private String type;
    
    /** X轴字段 */
    private String xAxis;
    
    /** Y轴字段 */
    private String yAxis;
    
    /** 表格展示的列 */
    private List<String> columns;
    
    /** 字段别名映射: 字段名 -> 中文名 */
    private Map<String, String> fieldAlias;
    
    /** 自定义结果表名（留空使用默认名称） */
    private String outputTable;
    
    /** 需要创建索引的字段列表 */
    private List<String> indexFields;

    // Getters and Setters
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getXAxis() { return xAxis; }
    public void setXAxis(String xAxis) { this.xAxis = xAxis; }

    public String getYAxis() { return yAxis; }
    public void setYAxis(String yAxis) { this.yAxis = yAxis; }

    public List<String> getColumns() { return columns; }
    public void setColumns(List<String> columns) { this.columns = columns; }

    public Map<String, String> getFieldAlias() { return fieldAlias; }
    public void setFieldAlias(Map<String, String> fieldAlias) { this.fieldAlias = fieldAlias; }

    public String getOutputTable() { return outputTable; }
    public void setOutputTable(String outputTable) { this.outputTable = outputTable; }

    public List<String> getIndexFields() { return indexFields; }
    public void setIndexFields(List<String> indexFields) { this.indexFields = indexFields; }
}
