package com.toolshare.mapper;

import com.toolshare.dto.toolbox.ToolBoxResponse;
import com.toolshare.entity.ToolBox;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * ToolBox 实体 -> ToolBoxResponse 的统一转换，管理员姓名按集合批量加载。
 */
@Component
public class ToolBoxResponseMapper {

    private final ReferenceDataLoader referenceDataLoader;

    public ToolBoxResponseMapper(ReferenceDataLoader referenceDataLoader) {
        this.referenceDataLoader = referenceDataLoader;
    }

    public ToolBoxResponse toResponse(ToolBox toolBox) {
        if (toolBox == null) {
            return null;
        }
        return toResponseList(List.of(toolBox)).get(0);
    }

    public List<ToolBoxResponse> toResponseList(List<ToolBox> toolBoxes) {
        if (toolBoxes == null || toolBoxes.isEmpty()) {
            return new ArrayList<>();
        }

        Map<Long, String> managerNameMap = referenceDataLoader.getUserNames(
                toolBoxes.stream().map(ToolBox::getManagerId).collect(Collectors.toList()));

        List<ToolBoxResponse> responses = new ArrayList<>();
        for (ToolBox toolBox : toolBoxes) {
            if (toolBox == null) {
                continue;
            }
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
