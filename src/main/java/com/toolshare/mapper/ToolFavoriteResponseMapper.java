package com.toolshare.mapper;

import com.toolshare.dto.toolfavorite.ToolFavoriteResponse;
import com.toolshare.entity.Tool;
import com.toolshare.entity.ToolFavorite;
import com.toolshare.repository.ToolRepository;
import com.toolshare.repository.UserRepository;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 工具收藏实体到 {@link ToolFavoriteResponse} 的公共转换器。
 */
@Component
public class ToolFavoriteResponseMapper extends AbstractResponseMapper<ToolFavorite, ToolFavoriteResponse> {

    private final ToolRepository toolRepository;
    private final UserRepository userRepository;

    public ToolFavoriteResponseMapper(ToolRepository toolRepository, UserRepository userRepository) {
        this.toolRepository = toolRepository;
        this.userRepository = userRepository;
    }

    @Override
    public List<ToolFavoriteResponse> toResponseList(List<ToolFavorite> favorites) {
        if (favorites == null || favorites.isEmpty()) {
            return new ArrayList<>();
        }

        Set<Long> toolIds = favorites.stream().map(ToolFavorite::getToolId).collect(Collectors.toSet());
        Set<Long> ownerIds = new HashSet<>();

        Map<Long, Tool> toolMap = new HashMap<>();
        toolRepository.findAllById(toolIds).forEach(tool -> {
            toolMap.put(tool.getId(), tool);
            ownerIds.add(tool.getOwnerId());
        });

        Map<Long, String> ownerNameMap = new HashMap<>();
        if (!ownerIds.isEmpty()) {
            userRepository.findAllById(ownerIds).forEach(user -> ownerNameMap.put(user.getId(), user.getUsername()));
        }

        List<ToolFavoriteResponse> responses = new ArrayList<>();
        for (ToolFavorite favorite : favorites) {
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
