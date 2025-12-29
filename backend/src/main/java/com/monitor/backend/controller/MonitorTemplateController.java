package com.monitor.backend.controller;

import com.monitor.backend.common.ApiResponse;
import com.monitor.backend.dto.template.MonitorTemplateRequest;
import com.monitor.backend.entity.MonitorTemplate;
import com.monitor.backend.exception.BusinessException;
import com.monitor.backend.exception.ErrorCode;
import com.monitor.backend.mapper.MonitorTemplateMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 监控模板控制器
 *
 * @author monitor-system
 */
@Tag(name = "监控模板", description = "监控模板的增删改查")
@RestController
@RequestMapping("/api/monitor-template")
public class MonitorTemplateController {

    private static final Logger log = LoggerFactory.getLogger(MonitorTemplateController.class);

    private final MonitorTemplateMapper templateMapper;

    public MonitorTemplateController(MonitorTemplateMapper templateMapper) {
        this.templateMapper = templateMapper;
    }

    @Operation(summary = "获取所有模板")
    @GetMapping
    public ApiResponse<List<MonitorTemplate>> getAll() {
        return ApiResponse.ok(templateMapper.findAll());
    }

    @Operation(summary = "获取模板详情")
    @GetMapping("/{id}")
    public ApiResponse<MonitorTemplate> getById(
            @Parameter(description = "模板ID") @PathVariable Long id) {
        MonitorTemplate template = templateMapper.findById(id);
        if (template == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "模板不存在");
        }
        return ApiResponse.ok(template);
    }

    @Operation(summary = "按分类获取模板")
    @GetMapping("/category/{category}")
    public ApiResponse<List<MonitorTemplate>> getByCategory(
            @Parameter(description = "分类名称") @PathVariable String category) {
        return ApiResponse.ok(templateMapper.findByCategory(category));
    }

    @Operation(summary = "获取系统模板")
    @GetMapping("/system")
    public ApiResponse<List<MonitorTemplate>> getSystemTemplates() {
        return ApiResponse.ok(templateMapper.findSystemTemplates());
    }

    @Operation(summary = "添加模板")
    @PostMapping
    public ApiResponse<Void> add(@RequestBody MonitorTemplateRequest request) {
        log.info("添加监控模板: name={}", request.getName());
        MonitorTemplate template = convertToEntity(request);
        template.setIsActive(1);
        template.setIsSystem(0);
        templateMapper.insert(template);
        return ApiResponse.ok("添加成功", null);
    }

    @Operation(summary = "更新模板")
    @PutMapping
    public ApiResponse<Void> update(@RequestBody MonitorTemplateRequest request) {
        log.info("更新监控模板: id={}", request.getId());
        MonitorTemplate template = convertToEntity(request);
        templateMapper.update(template);
        return ApiResponse.ok("更新成功", null);
    }

    @Operation(summary = "删除模板")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(
            @Parameter(description = "模板ID") @PathVariable Long id) {
        log.info("删除监控模板: id={}", id);
        templateMapper.deleteById(id);
        return ApiResponse.ok();
    }

    /**
     * 将请求DTO转换为实体
     */
    private MonitorTemplate convertToEntity(MonitorTemplateRequest request) {
        MonitorTemplate template = new MonitorTemplate();
        template.setId(request.getId());
        template.setName(request.getName());
        template.setCategory(request.getCategory());
        template.setCollectScript(request.getCollectScript());
        template.setDefaultThreshold(request.getDefaultThreshold());
        template.setDefaultCron(request.getDefaultCron());
        template.setAlarmTemplateId(request.getAlarmTemplateId());
        template.setIsActive(request.getIsActive());
        return template;
    }
}
