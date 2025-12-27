package com.monitor.backend.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 图表配置 DTO
 * <p>
 * 用于解析 MonitorTask.chartConfig JSON 字段
 * </p>
 *
 */
@Data
public class ChartConfigDto {

    /** 图表类型: bar, line, table, pivot 等 */
    private String type;

    /** X轴字段 */
    private String xAxis;

    /** Y轴字段 */
    private String yAxis;

    /** 展示的列 */
    private List<String> columns;

    /** 字段别名映射: 字段名 -> 中文名 */
    private Map<String, String> fieldAlias;

    /** 数据源类型: SQL WORKFLOW 等 */
    private String sourceType;

    /** 工作流 ID */
    private Long workflowId;

    /** 查询限制数量 */
    private Integer queryLimit;

    /** 是否启用分组 */
    private Boolean enableGrouping;

    /** 分组字段 */
    private String groupByField;

    /** 数值字段 */
    private String valueField;

    /** 聚合方法: SUM, COUNT, AVG 等 */
    private String aggregateMethod;

    /** 显示类型 */
    private String displayType;

    /** 透视表行字段 */
    private String pivotRowField;

    /** 透视表列字段 */
    private String pivotColField;

    /** 透视表数值字段 */
    private String pivotValueField;

    /** 透视表聚合方法 */
    private String pivotAggMethod;

    /** 自定义结果表名 */
    private String outputTable;

    /** 需要创建索引的字段列表 */
    private List<String> indexFields;

}
