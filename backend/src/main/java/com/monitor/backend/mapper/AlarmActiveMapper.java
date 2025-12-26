package com.monitor.backend.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.monitor.backend.entity.AlarmActive;

/**
 * 活跃告警 Mapper
 */
@Mapper
public interface AlarmActiveMapper {

    /**
     * 根据任务ID和类型查询活跃告警
     */
    AlarmActive findByTaskAndType(@Param("taskId") Long taskId, @Param("taskType") String taskType);

    /**
     * 根据ID查询
     */
    AlarmActive findById(@Param("id") Long id);

    /**
     * 查询所有活跃告警（非 RESOLVED 状态）
     */
    List<AlarmActive> findAllActive();

    /**
     * 按状态查询
     */
    List<AlarmActive> findByStatus(@Param("status") String status);

    /**
     * 查询需要升级的告警
     */
    List<AlarmActive> findNeedEscalation(@Param("minutes") int minutes);

    /**
     * 插入新告警
     */
    int insert(AlarmActive alarm);

    /**
     * 更新告警
     */
    int update(AlarmActive alarm);

    /**
     * 更新状态
     */
    int updateStatus(@Param("id") Long id, @Param("status") String status);
}
