package com.monitor.backend.controller;

import com.monitor.backend.entity.MonitorTemplate;
import com.monitor.backend.mapper.MonitorTemplateMapper;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/monitor-template")
public class MonitorTemplateController {
    
    private final MonitorTemplateMapper templateMapper;
    
    public MonitorTemplateController(MonitorTemplateMapper templateMapper) {
        this.templateMapper = templateMapper;
    }
    
    @GetMapping
    public List<MonitorTemplate> getAll() {
        return templateMapper.findAll();
    }
    
    @GetMapping("/{id}")
    public MonitorTemplate getById(@PathVariable Long id) {
        return templateMapper.findById(id);
    }
    
    @GetMapping("/category/{category}")
    public List<MonitorTemplate> getByCategory(@PathVariable String category) {
        return templateMapper.findByCategory(category);
    }
    
    @GetMapping("/system")
    public List<MonitorTemplate> getSystemTemplates() {
        return templateMapper.findSystemTemplates();
    }
    
    @PostMapping
    public String add(@RequestBody MonitorTemplate template) {
        template.setIsActive(1);
        template.setIsSystem(0);
        templateMapper.insert(template);
        return "success";
    }
    
    @PutMapping
    public String update(@RequestBody MonitorTemplate template) {
        templateMapper.update(template);
        return "success";
    }
    
    @DeleteMapping("/{id}")
    public String delete(@PathVariable Long id) {
        templateMapper.deleteById(id);
        return "success";
    }
}
