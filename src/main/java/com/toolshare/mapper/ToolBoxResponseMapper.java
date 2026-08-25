package com.toolshare.mapper;

import com.toolshare.dto.toolbox.ToolBoxResponse;
import com.toolshare.entity.ToolBox;
import com.toolshare.repository.UserRepository;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 工具箱实体到 {@link ToolBoxResponse} 的公共转换器。
 */
@Component
public class ToolBoxResponseMapper extends AbstractResponseMapper<ToolBox, ToolBoxResponse> {

    private final UserRepository userRepository;

    public ToolBoxResponseMapper(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
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
}
