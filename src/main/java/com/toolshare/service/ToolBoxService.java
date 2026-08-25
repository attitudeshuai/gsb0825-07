package com.toolshare.service;

import com.toolshare.dto.PageResponse;
import com.toolshare.dto.toolbox.CreateToolBoxRequest;
import com.toolshare.dto.toolbox.ToolBoxResponse;
import com.toolshare.dto.toolbox.UpdateToolBoxRequest;
import com.toolshare.entity.Tool;
import com.toolshare.entity.ToolBox;
import com.toolshare.entity.ToolStatus;
import com.toolshare.entity.User;
import com.toolshare.exception.BadRequestException;
import com.toolshare.exception.ResourceNotFoundException;
import com.toolshare.repository.ToolBoxRepository;
import com.toolshare.repository.ToolRepository;
import com.toolshare.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ToolBoxService {

    private final ToolBoxRepository toolBoxRepository;
    private final ToolRepository toolRepository;
    private final UserRepository userRepository;

    public ToolBoxService(ToolBoxRepository toolBoxRepository, ToolRepository toolRepository, UserRepository userRepository) {
        this.toolBoxRepository = toolBoxRepository;
        this.toolRepository = toolRepository;
        this.userRepository = userRepository;
    }

    public PageResponse<ToolBoxResponse> getAllToolBoxes(String keyword, Boolean isActive, int page, int size, String sortBy, String sortDir) {
        Sort sort = sortDir.equalsIgnoreCase("desc") ? Sort.by(sortBy).descending() : Sort.by(sortBy).ascending();
        Pageable pageable = PageRequest.of(page, size, sort);

        Page<ToolBox> toolBoxPage = toolBoxRepository.search(keyword, isActive, pageable);
        List<ToolBoxResponse> responseList = toResponseList(toolBoxPage.getContent());
        return PageResponse.of(responseList, toolBoxPage.getTotalElements(), toolBoxPage.getNumber(), toolBoxPage.getSize());
    }

    public ToolBoxResponse getToolBoxById(Long id) {
        ToolBox toolBox = toolBoxRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("工具箱不存在"));
        return toResponse(toolBox);
    }

    @Transactional
    public ToolBoxResponse createToolBox(CreateToolBoxRequest request, Long managerId) {
        if (request.getCode() != null && !request.getCode().trim().isEmpty()) {
            if (toolBoxRepository.existsByCode(request.getCode())) {
                throw new BadRequestException("工具箱编码已存在：" + request.getCode());
            }
        }

        ToolBox toolBox = new ToolBox();
        toolBox.setName(request.getName());
        toolBox.setLocation(request.getLocation());
        toolBox.setCode(request.getCode());
        toolBox.setImage(request.getImage());
        toolBox.setManagerId(managerId);
        toolBox.setIsActive(true);

        ToolBox savedToolBox = toolBoxRepository.save(toolBox);
        return toResponse(savedToolBox);
    }

    @Transactional
    public ToolBoxResponse updateToolBox(Long id, UpdateToolBoxRequest request, Long currentUserId) {
        ToolBox toolBox = toolBoxRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("工具箱不存在"));

        if (!toolBox.getManagerId().equals(currentUserId)) {
            throw new BadRequestException("无权修改此工具箱");
        }

        if (request.getCode() != null && !request.getCode().trim().isEmpty()) {
            if (!request.getCode().equals(toolBox.getCode()) && toolBoxRepository.existsByCode(request.getCode())) {
                throw new BadRequestException("工具箱编码已存在：" + request.getCode());
            }
        }

        if (request.getName() != null) {
            toolBox.setName(request.getName());
        }
        if (request.getLocation() != null) {
            toolBox.setLocation(request.getLocation());
        }
        if (request.getCode() != null) {
            toolBox.setCode(request.getCode());
        }
        if (request.getImage() != null) {
            toolBox.setImage(request.getImage());
        }
        if (request.getIsActive() != null) {
            updateToolBoxActiveInternal(toolBox, request.getIsActive());
        }

        ToolBox savedToolBox = toolBoxRepository.save(toolBox);
        return toResponse(savedToolBox);
    }

    @Transactional
    public void deleteToolBox(Long id, Long currentUserId) {
        ToolBox toolBox = toolBoxRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("工具箱不存在"));

        if (!toolBox.getManagerId().equals(currentUserId)) {
            throw new BadRequestException("无权删除此工具箱");
        }

        toolBoxRepository.delete(toolBox);
    }

    private List<ToolBoxResponse> toResponseList(List<ToolBox> toolBoxes) {
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
            responses.add(mapToToolBoxResponse(toolBox, managerNameMap));
        }
        return responses;
    }

    private ToolBoxResponse toResponse(ToolBox toolBox) {
        return toResponseList(List.of(toolBox)).get(0);
    }

    private ToolBoxResponse mapToToolBoxResponse(ToolBox toolBox, Map<Long, String> managerNameMap) {
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
        return response;
    }

    private void updateToolBoxActiveInternal(ToolBox toolBox, Boolean isActive) {
        boolean wasActive = Boolean.TRUE.equals(toolBox.getIsActive());
        boolean willBeActive = Boolean.TRUE.equals(isActive);

        if (wasActive == willBeActive) {
            return;
        }

        toolBox.setIsActive(isActive);

        if (wasActive && !willBeActive) {
            List<Tool> tools = toolRepository.findByBoxId(toolBox.getId());
            for (Tool tool : tools) {
                if (tool.getStatus() == ToolStatus.AVAILABLE) {
                    tool.setStatusBeforeBoxDeactivated(tool.getStatus());
                    tool.setStatus(ToolStatus.MAINTENANCE);
                    toolRepository.save(tool);
                }
            }
        } else if (!wasActive && willBeActive) {
            List<Tool> tools = toolRepository.findByBoxId(toolBox.getId());
            for (Tool tool : tools) {
                if (tool.getStatus() == ToolStatus.MAINTENANCE
                        && tool.getStatusBeforeBoxDeactivated() == ToolStatus.AVAILABLE) {
                    tool.setStatus(tool.getStatusBeforeBoxDeactivated());
                    tool.setStatusBeforeBoxDeactivated(null);
                    toolRepository.save(tool);
                }
            }
        }
    }

    @Transactional
    public ToolBoxResponse adminUpdateToolBoxActive(Long id, Boolean isActive) {
        ToolBox toolBox = toolBoxRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("工具箱不存在"));

        boolean wasActive = Boolean.TRUE.equals(toolBox.getIsActive());
        boolean willBeActive = Boolean.TRUE.equals(isActive);

        if (!wasActive && !willBeActive) {
            throw new BadRequestException("工具箱已停用");
        }
        if (wasActive && willBeActive) {
            throw new BadRequestException("工具箱已激活");
        }

        updateToolBoxActiveInternal(toolBox, isActive);

        ToolBox savedToolBox = toolBoxRepository.save(toolBox);
        return toResponse(savedToolBox);
    }

    public boolean isToolBoxManager(Long toolBoxId, Long userId) {
        return toolBoxRepository.findById(toolBoxId)
                .map(toolBox -> toolBox.getManagerId().equals(userId))
                .orElse(false);
    }
}
