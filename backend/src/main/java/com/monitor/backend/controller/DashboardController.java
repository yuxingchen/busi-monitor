package com.monitor.backend.controller;

import com.monitor.backend.common.ApiResponse;
import com.monitor.backend.dto.dashboard.DashboardRequest;
import com.monitor.backend.entity.Dashboard;
import com.monitor.backend.entity.DashboardWidget;
import com.monitor.backend.exception.BusinessException;
import com.monitor.backend.exception.ErrorCode;
import com.monitor.backend.mapper.DashboardMapper;
import com.monitor.backend.mapper.DashboardWidgetMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 仪表盘控制器
 *
 * @author monitor-system
 */
@Tag(name = "仪表盘管理", description = "仪表盘和组件的增删改查")
@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private static final Logger log = LoggerFactory.getLogger(DashboardController.class);

    private final DashboardMapper dashboardMapper;
    private final DashboardWidgetMapper widgetMapper;

    public DashboardController(DashboardMapper dashboardMapper, DashboardWidgetMapper widgetMapper) {
        this.dashboardMapper = dashboardMapper;
        this.widgetMapper = widgetMapper;
    }

    // ========== Dashboard CRUD ==========

    @Operation(summary = "获取所有仪表盘")
    @GetMapping
    public ApiResponse<List<Dashboard>> list() {
        return ApiResponse.ok(dashboardMapper.findAll());
    }

    @Operation(summary = "获取仪表盘详情（含组件）")
    @GetMapping("/{id}")
    public ApiResponse<Map<String, Object>> getById(
            @Parameter(description = "仪表盘ID") @PathVariable Long id) {
        Dashboard dashboard = dashboardMapper.findById(id);
        if (dashboard == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "仪表盘不存在");
        }
        Map<String, Object> data = new HashMap<>();
        data.put("dashboard", dashboard);
        data.put("widgets", widgetMapper.findByDashboardId(id));
        return ApiResponse.ok(data);
    }

    @Operation(summary = "获取默认仪表盘")
    @GetMapping("/default")
    public ApiResponse<Map<String, Object>> getDefault() {
        Dashboard dashboard = dashboardMapper.findDefault();
        Map<String, Object> data = new HashMap<>();
        if (dashboard != null) {
            data.put("dashboard", dashboard);
            data.put("widgets", widgetMapper.findByDashboardId(dashboard.getId()));
        }
        return ApiResponse.ok(data);
    }

    @Operation(summary = "创建仪表盘")
    @PostMapping
    public ApiResponse<Dashboard> create(@RequestBody DashboardRequest request) {
        log.info("创建仪表盘: name={}", request.getName());
        Dashboard dashboard = convertToEntity(request);
        if (dashboard.getGridCols() == null) dashboard.setGridCols(24);
        if (dashboard.getGridRows() == null) dashboard.setGridRows(12);
        if (dashboard.getCellHeight() == null) dashboard.setCellHeight(60);
        if (dashboard.getIsDefault() == null) dashboard.setIsDefault(0);
        if (dashboard.getIsActive() == null) dashboard.setIsActive(1);
        dashboardMapper.insert(dashboard);
        return ApiResponse.ok(dashboard);
    }

    @Operation(summary = "更新仪表盘")
    @PutMapping
    public ApiResponse<Dashboard> update(@RequestBody DashboardRequest request) {
        log.info("更新仪表盘: id={}", request.getId());
        Dashboard dashboard = convertToEntity(request);
        if (request.getIsDefault() != null && request.getIsDefault() == 1) {
            dashboardMapper.clearOtherDefaults(request.getId());
        }
        dashboardMapper.update(dashboard);
        return ApiResponse.ok(dashboard);
    }

    @Operation(summary = "删除仪表盘")
    @DeleteMapping("/{id}")
    @Transactional
    public ApiResponse<Void> delete(
            @Parameter(description = "仪表盘ID") @PathVariable Long id) {
        log.info("删除仪表盘: id={}", id);
        widgetMapper.deleteByDashboardId(id);
        dashboardMapper.deleteById(id);
        return ApiResponse.ok();
    }

    // ========== Widget CRUD ==========

    @Operation(summary = "获取仪表盘的所有组件")
    @GetMapping("/{dashboardId}/widgets")
    public ApiResponse<List<DashboardWidget>> getWidgets(
            @Parameter(description = "仪表盘ID") @PathVariable Long dashboardId) {
        return ApiResponse.ok(widgetMapper.findByDashboardId(dashboardId));
    }

    @Operation(summary = "添加组件到仪表盘")
    @PostMapping("/{dashboardId}/widget")
    public ApiResponse<DashboardWidget> addWidget(
            @Parameter(description = "仪表盘ID") @PathVariable Long dashboardId,
            @RequestBody DashboardWidget widget) {
        widget.setDashboardId(dashboardId);
        if (widget.getWidgetType() == null) widget.setWidgetType("TASK");
        if (widget.getGridW() == null) widget.setGridW(4);
        if (widget.getGridH() == null) widget.setGridH(3);
        if (widget.getZIndex() == null) widget.setZIndex(0);
        widgetMapper.insert(widget);
        return ApiResponse.ok(widget);
    }

    @Operation(summary = "更新组件")
    @PutMapping("/widget")
    public ApiResponse<DashboardWidget> updateWidget(@RequestBody DashboardWidget widget) {
        widgetMapper.update(widget);
        return ApiResponse.ok(widget);
    }

    @Operation(summary = "删除组件")
    @DeleteMapping("/widget/{id}")
    public ApiResponse<Void> deleteWidget(
            @Parameter(description = "组件ID") @PathVariable Long id) {
        widgetMapper.deleteById(id);
        return ApiResponse.ok();
    }

    // ========== 批量保存布局 ==========

    @Operation(summary = "批量保存仪表盘布局")
    @PostMapping("/{dashboardId}/layout")
    @Transactional
    public ApiResponse<Map<String, Object>> saveLayout(
            @Parameter(description = "仪表盘ID") @PathVariable Long dashboardId,
            @RequestBody List<DashboardWidget> widgets) {
        log.info("保存仪表盘布局: dashboardId={}, widgetCount={}", dashboardId, widgets != null ? widgets.size() : 0);
        
        // 清空现有布局
        widgetMapper.deleteByDashboardId(dashboardId);

        // 批量插入新布局
        if (widgets != null && !widgets.isEmpty()) {
            widgets.forEach(w -> {
                w.setDashboardId(dashboardId);
                if (w.getWidgetType() == null) w.setWidgetType("TASK");
                if (w.getGridW() == null) w.setGridW(4);
                if (w.getGridH() == null) w.setGridH(3);
                if (w.getZIndex() == null) w.setZIndex(0);
            });
            widgetMapper.batchInsert(widgets);
        }

        Map<String, Object> data = new HashMap<>();
        data.put("widgetCount", widgets != null ? widgets.size() : 0);
        return ApiResponse.ok("布局保存成功", data);
    }

    /**
     * 将仪表盘请求DTO转换为实体
     */
    private Dashboard convertToEntity(DashboardRequest request) {
        Dashboard dashboard = new Dashboard();
        dashboard.setId(request.getId());
        dashboard.setName(request.getName());
        dashboard.setGridCols(request.getGridCols());
        dashboard.setGridRows(request.getGridRows());
        dashboard.setCellHeight(request.getCellHeight());
        dashboard.setIsDefault(request.getIsDefault());
        dashboard.setIsActive(request.getIsActive());
        return dashboard;
    }
}
