package com.monitor.backend.controller;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.monitor.backend.alarm.AlarmService;
import com.monitor.backend.entity.AlarmActive;
import com.monitor.backend.entity.AlarmChannel;
import com.monitor.backend.entity.AlarmHistory;
import com.monitor.backend.entity.AlarmTemplate;
import com.monitor.backend.mapper.AlarmChannelMapper;
import com.monitor.backend.mapper.AlarmHistoryMapper;
import com.monitor.backend.mapper.AlarmTemplateMapper;

@RestController
@RequestMapping("/api/alarm")
public class AlarmController {

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

    /**
     * 获取所有活跃告警
     */
    @GetMapping("/active")
    public List<AlarmActive> getActiveAlarms() {
        return alarmService.getActiveAlarms();
    }

    /**
     * 确认告警
     */
    @PostMapping("/active/{id}/acknowledge")
    public Map<String, Object> acknowledgeAlarm(@PathVariable Long id,
            @RequestParam(defaultValue = "admin") String acknowledgeBy) {
        Map<String, Object> result = new HashMap<>();
        try {
            alarmService.acknowledgeAlarm(id, acknowledgeBy);
            result.put("status", "success");
            result.put("message", "告警已确认");
        } catch (Exception e) {
            result.put("status", "error");
            result.put("message", e.getMessage());
        }
        return result;
    }

    /**
     * 手动抑制告警
     */
    @PostMapping("/active/{id}/suppress")
    public Map<String, Object> suppressAlarm(@PathVariable Long id,
            @RequestParam(defaultValue = "30") int minutes) {
        Map<String, Object> result = new HashMap<>();
        try {
            alarmService.suppressAlarm(id, minutes);
            result.put("status", "success");
            result.put("message", "告警已抑制 " + minutes + " 分钟");
        } catch (Exception e) {
            result.put("status", "error");
            result.put("message", e.getMessage());
        }
        return result;
    }

    // ============ Channel APIs ============

    @GetMapping("/channel")
    public List<AlarmChannel> getAllChannels() {
        return channelMapper.findAll();
    }

    @GetMapping("/channel/active")
    public List<AlarmChannel> getActiveChannels() {
        return channelMapper.findAllActive();
    }

    @GetMapping("/channel/{id}")
    public AlarmChannel getChannel(@PathVariable Long id) {
        return channelMapper.findById(id);
    }

    @PostMapping("/channel")
    public String addChannel(@RequestBody AlarmChannel channel) {
        channel.setCreateTime(LocalDateTime.now());
        channel.setUpdateTime(LocalDateTime.now());
        channelMapper.insert(channel);
        return "success";
    }

    @PutMapping("/channel")
    public String updateChannel(@RequestBody AlarmChannel channel) {
        channel.setUpdateTime(LocalDateTime.now());
        channelMapper.update(channel);
        return "success";
    }

    @DeleteMapping("/channel/{id}")
    public String deleteChannel(@PathVariable Long id) {
        channelMapper.deleteById(id);
        return "success";
    }

    // ============ Template APIs ============

    @GetMapping("/template")
    public List<AlarmTemplate> getAllTemplates() {
        return templateMapper.findAll();
    }

    @GetMapping("/template/{id}")
    public AlarmTemplate getTemplate(@PathVariable Long id) {
        return templateMapper.findById(id);
    }

    @PostMapping("/template")
    public String addTemplate(@RequestBody AlarmTemplate template) {
        template.setCreateTime(LocalDateTime.now());
        template.setUpdateTime(LocalDateTime.now());
        templateMapper.insert(template);
        return "success";
    }

    @PutMapping("/template")
    public String updateTemplate(@RequestBody AlarmTemplate template) {
        template.setUpdateTime(LocalDateTime.now());
        templateMapper.update(template);
        return "success";
    }

    @DeleteMapping("/template/{id}")
    public String deleteTemplate(@PathVariable Long id) {
        templateMapper.deleteById(id);
        return "success";
    }

    // ============ History APIs ============

    @GetMapping("/history")
    public List<AlarmHistory> getRecentHistory(@RequestParam(defaultValue = "100") int limit) {
        return historyMapper.findRecent(limit);
    }

    @GetMapping("/history/page")
    public Map<String, Object> getHistoryPage(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        PageHelper.startPage(page, pageSize);
        List<AlarmHistory> list = historyMapper.findAll();
        PageInfo<AlarmHistory> pageInfo = new PageInfo<>(list);
        Map<String, Object> result = new HashMap<>();
        result.put("records", pageInfo.getList());
        result.put("total", pageInfo.getTotal());
        result.put("page", pageInfo.getPageNum());
        result.put("pageSize", pageInfo.getPageSize());
        return result;
    }

    @GetMapping("/history/task/{taskId}")
    public List<AlarmHistory> getHistoryByTask(@PathVariable Long taskId,
            @RequestParam(defaultValue = "50") int limit) {
        return historyMapper.findByTaskId(taskId, limit);
    }
}
