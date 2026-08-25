package com.toolshare.service.mapper;

import com.toolshare.dto.PageResponse;
import com.toolshare.dto.toolfavorite.ToolFavoriteResponse;
import com.toolshare.entity.Tool;
import com.toolshare.entity.ToolFavorite;
import com.toolshare.repository.ToolRepository;
import com.toolshare.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * ToolFavorite -> ToolFavoriteResponse 的统一转换组件。
 * 单条与批量共用同一套字段映射，工具信息、所有者姓名按 ID 批量加载，避免 N+1。
 */
@Component
public class ToolFavoriteResponseMapper {

    private final ToolRepository toolRepository;
    private final UserRepository userRepository;

    public ToolFavoriteResponseMapper(ToolRepository toolRepository, UserRepository userRepository) {
        this.toolRepository = toolRepository;
        this.userRepository = userRepository;
    }

    public ToolFavoriteResponse toResponse(ToolFavorite favorite) {
        return toResponseList(Collections.singletonList(favorite)).get(0);
    }

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

    public PageResponse<ToolFavoriteResponse> toPageResponse(Page<ToolFavorite> page) {
        return PageResponse.of(toResponseList(page.getContent()),
                page.getTotalElements(), page.getNumber(), page.getSize());
    }
}
