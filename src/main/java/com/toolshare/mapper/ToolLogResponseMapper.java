package com.toolshare.mapper;

import com.toolshare.dto.toollog.ToolLogResponse;
import com.toolshare.entity.ToolLog;
import com.toolshare.repository.ToolRepository;
import com.toolshare.repository.UserRepository;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 使用日志实体到 {@link ToolLogResponse} 的公共转换器。
 */
@Component
public class ToolLogResponseMapper extends AbstractResponseMapper<ToolLog, ToolLogResponse> {

    private final ToolRepository toolRepository;
    private final UserRepository userRepository;

    public ToolLogResponseMapper(ToolRepository toolRepository, UserRepository userRepository) {
        this.toolRepository = toolRepository;
        this.userRepository = userRepository;
    }

    @Override
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
}
