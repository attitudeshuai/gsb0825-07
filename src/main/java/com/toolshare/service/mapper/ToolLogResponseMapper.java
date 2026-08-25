package com.toolshare.service.mapper;

import com.toolshare.dto.PageResponse;
import com.toolshare.dto.toollog.ToolLogResponse;
import com.toolshare.entity.ToolLog;
import com.toolshare.repository.ToolRepository;
import com.toolshare.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * ToolLog -> ToolLogResponse 的统一转换组件。
 * 单条与批量共用同一套字段映射，工具名、用户名按 ID 批量加载，避免 N+1。
 */
@Component
public class ToolLogResponseMapper {

    private final ToolRepository toolRepository;
    private final UserRepository userRepository;

    public ToolLogResponseMapper(ToolRepository toolRepository, UserRepository userRepository) {
        this.toolRepository = toolRepository;
        this.userRepository = userRepository;
    }

    public ToolLogResponse toResponse(ToolLog toolLog) {
        return toResponseList(Collections.singletonList(toolLog)).get(0);
    }

    public List<ToolLogResponse> toResponseList(List<ToolLog> toolLogs) {
        if (toolLogs == null || toolLogs.isEmpty()) {
            return new ArrayList<>();
        }

        Set<Long> toolIds = toolLogs.stream().map(ToolLog::getToolId).collect(Collectors.toSet());
        Set<Long> userIds = toolLogs.stream().map(ToolLog::getUserId).collect(Collectors.toSet());

        Map<Long, String> toolNameMap = new HashMap<>();
        if (!toolIds.isEmpty()) {
            toolRepository.findAllById(toolIds).forEach(t -> toolNameMap.put(t.getId(), t.getName()));
        }
        Map<Long, String> userNameMap = new HashMap<>();
        if (!userIds.isEmpty()) {
            userRepository.findAllById(userIds).forEach(u -> userNameMap.put(u.getId(), u.getUsername()));
        }

        List<ToolLogResponse> responses = new ArrayList<>();
        for (ToolLog toolLog : toolLogs) {
            ToolLogResponse response = new ToolLogResponse();
            response.setId(toolLog.getId());
            response.setToolId(toolLog.getToolId());
            response.setUserId(toolLog.getUserId());
            response.setAction(toolLog.getAction());
            response.setDescription(toolLog.getDescription());
            response.setCreatedAt(toolLog.getCreatedAt());
            response.setToolName(toolNameMap.get(toolLog.getToolId()));
            response.setUserName(userNameMap.get(toolLog.getUserId()));
            responses.add(response);
        }
        return responses;
    }

    public PageResponse<ToolLogResponse> toPageResponse(Page<ToolLog> page) {
        return PageResponse.of(toResponseList(page.getContent()),
                page.getTotalElements(), page.getNumber(), page.getSize());
    }
}
