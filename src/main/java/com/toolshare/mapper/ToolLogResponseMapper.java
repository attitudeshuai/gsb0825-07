package com.toolshare.mapper;

import com.toolshare.dto.toollog.ToolLogResponse;
import com.toolshare.entity.ToolLog;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * ToolLog 实体 -> ToolLogResponse 的统一转换，工具名/用户名按集合批量加载。
 */
@Component
public class ToolLogResponseMapper {

    private final ReferenceDataLoader referenceDataLoader;

    public ToolLogResponseMapper(ReferenceDataLoader referenceDataLoader) {
        this.referenceDataLoader = referenceDataLoader;
    }

    public ToolLogResponse toResponse(ToolLog toolLog) {
        if (toolLog == null) {
            return null;
        }
        return toResponseList(List.of(toolLog)).get(0);
    }

    public List<ToolLogResponse> toResponseList(List<ToolLog> toolLogs) {
        if (toolLogs == null || toolLogs.isEmpty()) {
            return new ArrayList<>();
        }

        Map<Long, String> toolNameMap = referenceDataLoader.getToolNames(
                toolLogs.stream().map(ToolLog::getToolId).collect(Collectors.toList()));
        Map<Long, String> userNameMap = referenceDataLoader.getUserNames(
                toolLogs.stream().map(ToolLog::getUserId).collect(Collectors.toList()));

        List<ToolLogResponse> responses = new ArrayList<>();
        for (ToolLog toolLog : toolLogs) {
            if (toolLog == null) {
                continue;
            }
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
