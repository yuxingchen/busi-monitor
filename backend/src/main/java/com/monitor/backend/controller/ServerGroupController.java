package com.monitor.backend.controller;

import com.monitor.backend.entity.ServerGroup;
import com.monitor.backend.mapper.ServerGroupMapper;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/server-group")
public class ServerGroupController {
    
    private final ServerGroupMapper groupMapper;
    
    public ServerGroupController(ServerGroupMapper groupMapper) {
        this.groupMapper = groupMapper;
    }
    
    @GetMapping
    public List<ServerGroup> getAll() {
        return groupMapper.findAll();
    }
    
    @GetMapping("/{id}")
    public ServerGroup getById(@PathVariable Long id) {
        return groupMapper.findById(id);
    }
    
    @PostMapping
    public String add(@RequestBody ServerGroup group) {
        group.setIsActive(1);
        groupMapper.insert(group);
        return "success";
    }
    
    @PutMapping
    public String update(@RequestBody ServerGroup group) {
        groupMapper.update(group);
        return "success";
    }
    
    @DeleteMapping("/{id}")
    public String delete(@PathVariable Long id) {
        groupMapper.deleteById(id);
        return "success";
    }
}
