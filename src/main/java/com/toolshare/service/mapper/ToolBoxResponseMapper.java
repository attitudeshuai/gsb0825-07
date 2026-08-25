package com.toolshare.service.mapper;

import com.toolshare.dto.PageResponse;
import com.toolshare.dto.toolbox.ToolBoxResponse;
import com.toolshare.entity.ToolBox;
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
 * ToolBox -> ToolBoxResponse 的统一转换组件。
 * 单条与批量共用同一套字段映射，管理员姓名按 ID 批量加载，避免 N+1。
 */
@Component
public class ToolBoxResponseMapper {

    private final UserRepository userRepository;

    public ToolBoxResponseMapper(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public ToolBoxResponse toResponse(ToolBox toolBox) {
        return toResponseList(Collections.singletonList(toolBox)).get(0);
    }

    public List<ToolBoxResponse> toResponseList(List<ToolBox> toolBoxes) {
        if (toolBoxes == null || toolBoxes.isEmpty()) {
            return new ArrayList<>();
        }

        Set<Long> managerIds = toolBoxes.stream().map(ToolBox::getManagerId).collect(Collectors.toSet());
        Map<Long, String> managerNameMap = new HashMap<>();
        if (!managerIds.isEmpty()) {
            userRepository.findAllById(managerIds).forEach(u -> managerNameMap.put(u.getId(), u.getUsername()));
        }

        List<ToolBoxResponse> responses = new ArrayList<>();
        for (ToolBox toolBox : toolBoxes) {
            ToolBoxResponse response = new ToolBoxResponse();
            response.setId(toolBox.getId());
            response.setName(toolBox.getName());
            response.setLocation(toolBox.getLocation());
            response.setManagerId(toolBox.getManagerId());
            response.setCode(toolBox.getCode());
            response.setImage(toolBox.getImage());
            response.setIsActive(toolBox.getIsActive());
            response.setCreatedAt(toolBox.getCreatedAt());
            response.setManagerName(managerNameMap.get(toolBox.getManagerId()));
            responses.add(response);
        }
        return responses;
    }

    public PageResponse<ToolBoxResponse> toPageResponse(Page<ToolBox> page) {
        return PageResponse.of(toResponseList(page.getContent()),
                page.getTotalElements(), page.getNumber(), page.getSize());
    }
}
