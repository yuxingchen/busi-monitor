package com.monitor.backend.controller;

import com.monitor.backend.common.ApiResponse;
import com.monitor.backend.entity.ServerGroup;
import com.monitor.backend.exception.BusinessException;
import com.monitor.backend.exception.ErrorCode;
import com.monitor.backend.mapper.ServerGroupMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 服务器分组控制器
 *
 * @author monitor-system
 */
@Tag(name = "服务器分组", description = "服务器分组的增删改查")
@RestController
@RequestMapping("/api/server-group")
public class ServerGroupController {

    private static final Logger log = LoggerFactory.getLogger(ServerGroupController.class);

    private final ServerGroupMapper groupMapper;

    public ServerGroupController(ServerGroupMapper groupMapper) {
        this.groupMapper = groupMapper;
    }

    @Operation(summary = "获取所有分组")
    @GetMapping
    public ApiResponse<List<ServerGroup>> listAll() {
        return ApiResponse.ok(groupMapper.findAll());
    }

    @Operation(summary = "获取分组详情")
    @GetMapping("/{id}")
    public ApiResponse<ServerGroup> getById(
            @Parameter(description = "分组ID") @PathVariable Long id) {
        ServerGroup group = groupMapper.findById(id);
        if (group == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "分组不存在");
        }
        return ApiResponse.ok(group);
    }

    @Operation(summary = "添加分组")
    @PostMapping
    public ApiResponse<Void> add(@RequestBody ServerGroup group) {
        log.info("添加服务器分组: name={}", group.getName());
        group.setIsActive(1);
        groupMapper.insert(group);
        return ApiResponse.ok("添加成功", null);
    }

    @Operation(summary = "更新分组")
    @PutMapping
    public ApiResponse<Void> update(@RequestBody ServerGroup group) {
        log.info("更新服务器分组: id={}", group.getId());
        groupMapper.update(group);
        return ApiResponse.ok("更新成功", null);
    }

    @Operation(summary = "删除分组")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(
            @Parameter(description = "分组ID") @PathVariable Long id) {
        log.info("删除服务器分组: id={}", id);
        groupMapper.deleteById(id);
        return ApiResponse.ok();
    }
}
