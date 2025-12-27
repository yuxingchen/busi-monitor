package com.monitor.backend.controller;

import java.time.LocalDateTime;
import java.util.List;

import com.monitor.backend.common.ApiResponse;
import com.monitor.backend.common.PageResult;
import com.monitor.backend.exception.BusinessException;
import com.monitor.backend.exception.ErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import com.monitor.backend.alarm.AlarmService;
import com.monitor.backend.entity.AlarmActive;
import com.monitor.backend.entity.AlarmChannel;
import com.monitor.backend.entity.AlarmHistory;
import com.monitor.backend.entity.AlarmTemplate;
import com.monitor.backend.mapper.AlarmChannelMapper;
import com.monitor.backend.mapper.AlarmHistoryMapper;
import com.monitor.backend.mapper.AlarmTemplateMapper;

/**
 * 告警管理控制器
 *
 * @author monitor-system
 */
@Tag(name = "告警管理", description = "活跃告警、告警渠道、告警模板、告警历史")
@RestController
@RequestMapping("/api/alarm")
public class AlarmController {

    private static final Logger log = LoggerFactory.getLogger(AlarmController.class);

    private final AlarmChannelMapper channelMapper;
    private final AlarmTemplateMapper templateMapper;
    private final AlarmHistoryMapper historyMapper;
    private final AlarmService alarmService;

    public AlarmController(AlarmChannelMapper channelMapper,
            AlarmTemplateMapper templateMapper,
            AlarmHistoryMapper historyMapper,
            AlarmService alarmService) {
        this.channelMapper = channelMapper;
        this.templateMapper = templateMapper;
        this.historyMapper = historyMapper;
        this.alarmService = alarmService;
    }

    // ============ Active Alarm APIs ============

    @Operation(summary = "获取活跃告警", description = "获取所有未恢复的活跃告警")
    @GetMapping("/active")
    public ApiResponse<List<AlarmActive>> getActiveAlarms() {
        return ApiResponse.ok(alarmService.getActiveAlarms());
    }

    @Operation(summary = "确认告警", description = "确认指定的活跃告警")
    @PostMapping("/active/{id}/acknowledge")
    public ApiResponse<Void> acknowledgeAlarm(
            @Parameter(description = "告警ID") @PathVariable Long id,
            @Parameter(description = "确认人") @RequestParam(defaultValue = "admin") String acknowledgeBy) {
        log.info("确认告警: id={}, acknowledgeBy={}", id, acknowledgeBy);
        alarmService.acknowledgeAlarm(id, acknowledgeBy);
        return ApiResponse.ok("告警已确认", null);
    }

    @Operation(summary = "抑制告警", description = "临时抑制告警指定时间")
    @PostMapping("/active/{id}/suppress")
    public ApiResponse<Void> suppressAlarm(
            @Parameter(description = "告警ID") @PathVariable Long id,
            @Parameter(description = "抑制时间(分钟)") @RequestParam(defaultValue = "30") int minutes) {
        log.info("抑制告警: id={}, minutes={}", id, minutes);
        alarmService.suppressAlarm(id, minutes);
        return ApiResponse.ok("告警已抑制 " + minutes + " 分钟", null);
    }

    // ============ Channel APIs ============

    @Operation(summary = "获取所有告警渠道")
    @GetMapping("/channel")
    public ApiResponse<List<AlarmChannel>> getAllChannels() {
        return ApiResponse.ok(channelMapper.findAll());
    }

    @Operation(summary = "获取启用的告警渠道")
    @GetMapping("/channel/active")
    public ApiResponse<List<AlarmChannel>> getActiveChannels() {
        return ApiResponse.ok(channelMapper.findAllActive());
    }

    @Operation(summary = "获取告警渠道详情")
    @GetMapping("/channel/{id}")
    public ApiResponse<AlarmChannel> getChannel(@PathVariable Long id) {
        AlarmChannel channel = channelMapper.findById(id);
        if (channel == null) {
            throw new BusinessException(ErrorCode.ALARM_CHANNEL_NOT_FOUND);
        }
        return ApiResponse.ok(channel);
    }

    @Operation(summary = "添加告警渠道")
    @PostMapping("/channel")
    public ApiResponse<Void> addChannel(@RequestBody AlarmChannel channel) {
        log.info("添加告警渠道: name={}, type={}", channel.getName(), channel.getType());
        channel.setCreateTime(LocalDateTime.now());
        channel.setUpdateTime(LocalDateTime.now());
        channelMapper.insert(channel);
        return ApiResponse.ok("添加成功", null);
    }

    @Operation(summary = "更新告警渠道")
    @PutMapping("/channel")
    public ApiResponse<Void> updateChannel(@RequestBody AlarmChannel channel) {
        log.info("更新告警渠道: id={}", channel.getId());
        channel.setUpdateTime(LocalDateTime.now());
        channelMapper.update(channel);
        return ApiResponse.ok("更新成功", null);
    }

    @Operation(summary = "删除告警渠道")
    @DeleteMapping("/channel/{id}")
    public ApiResponse<Void> deleteChannel(@PathVariable Long id) {
        log.info("删除告警渠道: id={}", id);
        channelMapper.deleteById(id);
        return ApiResponse.ok();
    }

    // ============ Template APIs ============

    @Operation(summary = "获取所有告警模板")
    @GetMapping("/template")
    public ApiResponse<List<AlarmTemplate>> getAllTemplates() {
        return ApiResponse.ok(templateMapper.findAll());
    }

    @Operation(summary = "获取告警模板详情")
    @GetMapping("/template/{id}")
    public ApiResponse<AlarmTemplate> getTemplate(@PathVariable Long id) {
        AlarmTemplate template = templateMapper.findById(id);
        if (template == null) {
            throw new BusinessException(ErrorCode.ALARM_TEMPLATE_NOT_FOUND);
        }
        return ApiResponse.ok(template);
    }

    @Operation(summary = "添加告警模板")
    @PostMapping("/template")
    public ApiResponse<Void> addTemplate(@RequestBody AlarmTemplate template) {
        log.info("添加告警模板: name={}", template.getName());
        template.setCreateTime(LocalDateTime.now());
        template.setUpdateTime(LocalDateTime.now());
        templateMapper.insert(template);
        return ApiResponse.ok("添加成功", null);
    }

    @Operation(summary = "更新告警模板")
    @PutMapping("/template")
    public ApiResponse<Void> updateTemplate(@RequestBody AlarmTemplate template) {
        log.info("更新告警模板: id={}", template.getId());
        template.setUpdateTime(LocalDateTime.now());
        templateMapper.update(template);
        return ApiResponse.ok("更新成功", null);
    }

    @Operation(summary = "删除告警模板")
    @DeleteMapping("/template/{id}")
    public ApiResponse<Void> deleteTemplate(@PathVariable Long id) {
        log.info("删除告警模板: id={}", id);
        templateMapper.deleteById(id);
        return ApiResponse.ok();
    }

    // ============ History APIs ============

    @Operation(summary = "获取最近告警历史")
    @GetMapping("/history")
    public ApiResponse<List<AlarmHistory>> getRecentHistory(
            @Parameter(description = "条数限制") @RequestParam(defaultValue = "100") int limit) {
        return ApiResponse.ok(historyMapper.findRecent(limit));
    }

    @Operation(summary = "分页查询告警历史")
    @GetMapping("/history/page")
    public ApiResponse<PageResult<AlarmHistory>> getHistoryPage(
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页条数") @RequestParam(defaultValue = "20") int pageSize) {
        
        com.github.pagehelper.PageHelper.startPage(page, pageSize);
        List<AlarmHistory> list = historyMapper.findAll();
        com.github.pagehelper.PageInfo<AlarmHistory> pageInfo = new com.github.pagehelper.PageInfo<>(list);
        
        return ApiResponse.ok(PageResult.of(list, pageInfo.getTotal(), page, pageSize));
    }

    @Operation(summary = "按任务查询告警历史")
    @GetMapping("/history/task/{taskId}")
    public ApiResponse<List<AlarmHistory>> getHistoryByTask(
            @PathVariable Long taskId,
            @Parameter(description = "条数限制") @RequestParam(defaultValue = "50") int limit) {
        return ApiResponse.ok(historyMapper.findByTaskId(taskId, limit));
    }
}
