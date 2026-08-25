package com.toolshare.mapper;

import com.toolshare.dto.toolfavorite.ToolFavoriteResponse;
import com.toolshare.entity.Tool;
import com.toolshare.entity.ToolFavorite;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * ToolFavorite 实体 -> ToolFavoriteResponse 的统一转换。
 * 收藏的工具快照字段与工具所有者名按集合批量加载。
 */
@Component
public class ToolFavoriteResponseMapper {

    private final ReferenceDataLoader referenceDataLoader;

    public ToolFavoriteResponseMapper(ReferenceDataLoader referenceDataLoader) {
        this.referenceDataLoader = referenceDataLoader;
    }

    public ToolFavoriteResponse toResponse(ToolFavorite favorite) {
        if (favorite == null) {
            return null;
        }
        return toResponseList(List.of(favorite)).get(0);
    }

    public List<ToolFavoriteResponse> toResponseList(List<ToolFavorite> favorites) {
        if (favorites == null || favorites.isEmpty()) {
            return new ArrayList<>();
        }

        List<ToolFavorite> validFavorites = favorites.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        if (validFavorites.isEmpty()) {
            return new ArrayList<>();
        }

        List<Long> toolIds = validFavorites.stream()
                .map(ToolFavorite::getToolId)
                .distinct()
                .collect(Collectors.toList());
        Map<Long, Tool> toolMap = referenceDataLoader.getTools(toolIds);

        Map<Long, String> ownerNameMap = referenceDataLoader.getUserNames(
                toolMap.values().stream().map(Tool::getOwnerId).collect(Collectors.toList()));

        List<ToolFavoriteResponse> responses = new ArrayList<>();
        for (ToolFavorite favorite : validFavorites) {
            ToolFavoriteResponse response = new ToolFavoriteResponse();
            response.setId(favorite.getId());
            response.setUserId(favorite.getUserId());
            response.setToolId(favorite.getToolId());
            response.setCreatedAt(favorite.getCreatedAt());

            Tool tool = toolMap.get(favorite.getToolId());
            if (tool != null) {
                response.setToolName(tool.getName());
                response.setToolCategory(tool.getCategory());
                response.setToolStatus(tool.getStatus());
                response.setToolImage(tool.getImage());
                response.setToolDescription(tool.getDescription());
                response.setToolOwnerId(tool.getOwnerId());
                response.setToolOwnerName(ownerNameMap.get(tool.getOwnerId()));
            }

            responses.add(response);
        }
        return responses;
    }
}
