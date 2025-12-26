package com.monitor.backend.controller;

import com.monitor.backend.entity.Dashboard;
import com.monitor.backend.entity.DashboardWidget;
import com.monitor.backend.mapper.DashboardMapper;
import com.monitor.backend.mapper.DashboardWidgetMapper;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardMapper dashboardMapper;
    private final DashboardWidgetMapper widgetMapper;

    public DashboardController(DashboardMapper dashboardMapper, DashboardWidgetMapper widgetMapper) {
        this.dashboardMapper = dashboardMapper;
        this.widgetMapper = widgetMapper;
    }

    // ========== Dashboard CRUD ==========

    @GetMapping
    public List<Dashboard> list() {
        return dashboardMapper.findAll();
    }

    @GetMapping("/{id}")
    public Map<String, Object> getById(@PathVariable Long id) {
        Map<String, Object> result = new HashMap<>();
        Dashboard dashboard = dashboardMapper.findById(id);
        if (dashboard != null) {
            result.put("dashboard", dashboard);
            result.put("widgets", widgetMapper.findByDashboardId(id));
        }
        return result;
    }

    @GetMapping("/default")
    public Map<String, Object> getDefault() {
        Dashboard dashboard = dashboardMapper.findDefault();
        Map<String, Object> result = new HashMap<>();
        if (dashboard != null) {
            result.put("dashboard", dashboard);
            result.put("widgets", widgetMapper.findByDashboardId(dashboard.getId()));
        }
        return result;
    }

    @PostMapping
    public Dashboard create(@RequestBody Dashboard dashboard) {
        if (dashboard.getGridCols() == null)
            dashboard.setGridCols(24);
        if (dashboard.getGridRows() == null)
            dashboard.setGridRows(12);
        if (dashboard.getCellHeight() == null)
            dashboard.setCellHeight(60);
        if (dashboard.getIsDefault() == null)
            dashboard.setIsDefault(0);
        if (dashboard.getIsActive() == null)
            dashboard.setIsActive(1);
        dashboardMapper.insert(dashboard);
        return dashboard;
    }

    @PutMapping
    public Dashboard update(@RequestBody Dashboard dashboard) {
        if (dashboard.getIsDefault() != null && dashboard.getIsDefault() == 1) {
            dashboardMapper.clearOtherDefaults(dashboard.getId());
        }
        dashboardMapper.update(dashboard);
        return dashboard;
    }

    @DeleteMapping("/{id}")
    @Transactional
    public void delete(@PathVariable Long id) {
        widgetMapper.deleteByDashboardId(id);
        dashboardMapper.deleteById(id);
    }

    // ========== Widget CRUD ==========

    @GetMapping("/{dashboardId}/widgets")
    public List<DashboardWidget> getWidgets(@PathVariable Long dashboardId) {
        return widgetMapper.findByDashboardId(dashboardId);
    }

    @PostMapping("/{dashboardId}/widget")
    public DashboardWidget addWidget(@PathVariable Long dashboardId, @RequestBody DashboardWidget widget) {
        widget.setDashboardId(dashboardId);
        if (widget.getWidgetType() == null)
            widget.setWidgetType("TASK");
        if (widget.getGridW() == null)
            widget.setGridW(4);
        if (widget.getGridH() == null)
            widget.setGridH(3);
        if (widget.getZIndex() == null)
            widget.setZIndex(0);
        widgetMapper.insert(widget);
        return widget;
    }

    @PutMapping("/widget")
    public DashboardWidget updateWidget(@RequestBody DashboardWidget widget) {
        widgetMapper.update(widget);
        return widget;
    }

    @DeleteMapping("/widget/{id}")
    public void deleteWidget(@PathVariable Long id) {
        widgetMapper.deleteById(id);
    }

    // ========== 批量保存布局 ==========

    @PostMapping("/{dashboardId}/layout")
    @Transactional
    public Map<String, Object> saveLayout(@PathVariable Long dashboardId, @RequestBody List<DashboardWidget> widgets) {
        // 清空现有布局
        widgetMapper.deleteByDashboardId(dashboardId);

        // 批量插入新布局
        if (widgets != null && !widgets.isEmpty()) {
            widgets.forEach(w -> {
                w.setDashboardId(dashboardId);
                if (w.getWidgetType() == null)
                    w.setWidgetType("TASK");
                if (w.getGridW() == null)
                    w.setGridW(4);
                if (w.getGridH() == null)
                    w.setGridH(3);
                if (w.getZIndex() == null)
                    w.setZIndex(0);
            });
            widgetMapper.batchInsert(widgets);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "布局保存成功");
        result.put("widgetCount", widgets != null ? widgets.size() : 0);
        return result;
    }
}
