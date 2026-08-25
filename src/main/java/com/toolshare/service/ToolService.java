package com.toolshare.service;

import com.toolshare.dto.PageResponse;
import com.toolshare.dto.tool.CompleteRepairRequest;
import com.toolshare.dto.tool.CreateToolRequest;
import com.toolshare.dto.tool.ReportToolRequest;
import com.toolshare.dto.tool.ToolResponse;
import com.toolshare.dto.tool.UpdateToolRequest;
import com.toolshare.dto.tool.UpdateToolStatusRequest;
import com.toolshare.entity.Tool;
import com.toolshare.entity.ToolBox;
import com.toolshare.entity.ToolLogAction;
import com.toolshare.entity.ToolStatus;
import com.toolshare.exception.BadRequestException;
import com.toolshare.exception.ResourceNotFoundException;
import com.toolshare.mapper.ToolResponseMapper;
import com.toolshare.repository.ToolBoxRepository;
import com.toolshare.repository.ToolRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

@Service
public class ToolService {

    private final ToolRepository toolRepository;
    private final ToolBoxRepository toolBoxRepository;
    private final ToolResponseMapper toolResponseMapper;
    private final ToolLogService toolLogService;

    public ToolService(ToolRepository toolRepository, ToolBoxRepository toolBoxRepository,
                       ToolResponseMapper toolResponseMapper, ToolLogService toolLogService) {
        this.toolRepository = toolRepository;
        this.toolBoxRepository = toolBoxRepository;
        this.toolResponseMapper = toolResponseMapper;
        this.toolLogService = toolLogService;
    }

    public PageResponse<ToolResponse> getAllTools(String keyword, String category, ToolStatus status, Long boxId,
                                                   int page, int size, String sortBy, String sortDir) {
        return getAllTools(keyword, category, status, boxId, null, page, size, sortBy, sortDir);
    }

    public PageResponse<ToolResponse> getAllTools(String keyword, String category, ToolStatus status, Long boxId,
                                                   List<Long> boxIds, int page, int size, String sortBy, String sortDir) {
        List<Long> effectiveBoxIds = resolveBoxIds(boxId, boxIds);

        if ("borrowCount".equals(sortBy)) {
            return getAllToolsSortedByBorrowCount(keyword, category, status, effectiveBoxIds, page, size, sortDir);
        }

        Sort sort = sortDir.equalsIgnoreCase("desc") ? Sort.by(sortBy).descending() : Sort.by(sortBy).ascending();
        Pageable pageable = PageRequest.of(page, size, sort);

        Page<Tool> toolPage;
        if (effectiveBoxIds != null && !effectiveBoxIds.isEmpty()) {
            toolPage = toolRepository.searchWithMultipleBoxes(keyword, category, status, effectiveBoxIds, pageable);
        } else if (boxId != null) {
            toolPage = toolRepository.search(keyword, category, status, boxId, pageable);
        } else {
            toolPage = toolRepository.search(keyword, category, status, null, pageable);
        }

        List<ToolResponse> responseList = toolResponseMapper.toResponseList(toolPage.getContent());
        return PageResponse.of(responseList, toolPage.getTotalElements(), toolPage.getNumber(), toolPage.getSize());
    }

    private List<Long> resolveBoxIds(Long boxId, List<Long> boxIds) {
        if (boxIds != null && !boxIds.isEmpty()) {
            return boxIds;
        }
        if (boxId != null) {
            return List.of(boxId);
        }
        return null;
    }

    private PageResponse<ToolResponse> getAllToolsSortedByBorrowCount(String keyword, String category, ToolStatus status,
                                                                       List<Long> boxIds, int page, int size, String sortDir) {
        List<Tool> allTools;
        if (boxIds != null && !boxIds.isEmpty()) {
            allTools = toolRepository.searchWithMultipleBoxes(keyword, category, status, boxIds, Pageable.unpaged()).getContent();
        } else {
            allTools = toolRepository.search(keyword, category, status, null, Pageable.unpaged()).getContent();
        }

        List<ToolResponse> allResponses = toolResponseMapper.toResponseList(allTools);

        Comparator<ToolResponse> borrowCountComparator = Comparator.comparing(
                response -> response.getBorrowCount() != null ? response.getBorrowCount() : 0L
        );
        if ("desc".equalsIgnoreCase(sortDir)) {
            borrowCountComparator = borrowCountComparator.reversed();
        }
        allResponses.sort(borrowCountComparator);

        long totalElements = allResponses.size();
        int fromIndex = Math.min(page * size, allResponses.size());
        int toIndex = Math.min(fromIndex + size, allResponses.size());
        List<ToolResponse> pageContent = allResponses.subList(fromIndex, toIndex);

        return PageResponse.of(pageContent, totalElements, page, size);
    }

    public ToolResponse getToolById(Long id) {
        Tool tool = toolRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("工具不存在"));
        return toolResponseMapper.toResponse(tool);
    }

    public PageResponse<ToolResponse> getMyTools(Long ownerId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<Tool> toolPage = toolRepository.findByOwnerId(ownerId, pageable);
        List<ToolResponse> responseList = toolResponseMapper.toResponseList(toolPage.getContent());
        return PageResponse.of(responseList, toolPage.getTotalElements(), toolPage.getNumber(), toolPage.getSize());
    }

    @Transactional
    public ToolResponse createTool(CreateToolRequest request, Long ownerId) {
        ToolBox toolBox = toolBoxRepository.findById(request.getBoxId())
                .orElseThrow(() -> new ResourceNotFoundException("工具箱不存在"));

        Tool tool = new Tool();
        tool.setBoxId(request.getBoxId());
        tool.setName(request.getName());
        tool.setCategory(request.getCategory());
        tool.setDescription(request.getDescription());
        tool.setImage(request.getImage());
        tool.setPurchaseDate(request.getPurchaseDate());
        tool.setOwnerId(ownerId);
        tool.setStatus(ToolStatus.AVAILABLE);
        tool.setMaxBorrowDays(request.getMaxBorrowDays());

        Tool savedTool = toolRepository.save(tool);
        return toolResponseMapper.toResponse(savedTool);
    }

    @Transactional
    public ToolResponse updateTool(Long id, UpdateToolRequest request, Long currentUserId) {
        Tool tool = toolRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("工具不存在"));

        if (!tool.getOwnerId().equals(currentUserId)) {
            throw new BadRequestException("无权修改此工具");
        }

        if (request.getBoxId() != null) {
            if (!toolBoxRepository.existsById(request.getBoxId())) {
                throw new ResourceNotFoundException("工具箱不存在");
            }
            tool.setBoxId(request.getBoxId());
        }
        if (request.getName() != null) {
            tool.setName(request.getName());
        }
        if (request.getCategory() != null) {
            tool.setCategory(request.getCategory());
        }
        if (request.getDescription() != null) {
            tool.setDescription(request.getDescription());
        }
        if (request.getImage() != null) {
            tool.setImage(request.getImage());
        }
        if (request.getPurchaseDate() != null) {
            tool.setPurchaseDate(request.getPurchaseDate());
        }
        if (request.getStatus() != null) {
            if (request.getStatus() == com.toolshare.entity.ToolStatus.DISABLED) {
                throw new BadRequestException("只有管理员可以禁用工具");
            }
            tool.setStatus(request.getStatus());
        }
        if (request.getMaxBorrowDays() != null) {
            tool.setMaxBorrowDays(request.getMaxBorrowDays());
        }

        Tool savedTool = toolRepository.save(tool);
        return toolResponseMapper.toResponse(savedTool);
    }

    @Transactional
    public ToolResponse updateToolStatus(Long id, UpdateToolStatusRequest request, Long currentUserId) {
        Tool tool = toolRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("工具不存在"));

        if (!tool.getOwnerId().equals(currentUserId)) {
            throw new BadRequestException("无权修改此工具状态");
        }

        if (request.getStatus() == com.toolshare.entity.ToolStatus.DISABLED) {
            throw new BadRequestException("只有管理员可以禁用工具");
        }

        tool.setStatus(request.getStatus());
        Tool savedTool = toolRepository.save(tool);
        return toolResponseMapper.toResponse(savedTool);
    }

    @Transactional
    public ToolResponse reportTool(Long id, ReportToolRequest request, Long currentUserId) {
        Tool tool = toolRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("工具不存在"));

        if (tool.getStatus() == ToolStatus.DISABLED) {
            throw new BadRequestException("工具已被禁用，无法报修");
        }

        if (tool.getStatus() == ToolStatus.MAINTENANCE) {
            throw new BadRequestException("工具已在维修中，无需重复报修");
        }

        tool.setStatus(ToolStatus.MAINTENANCE);
        Tool savedTool = toolRepository.save(tool);

        toolLogService.createLogInternal(id, currentUserId, ToolLogAction.REPORT, request.getDescription());

        return toolResponseMapper.toResponse(savedTool);
    }

    @Transactional
    public ToolResponse completeRepair(Long id, CompleteRepairRequest request, Long currentUserId) {
        Tool tool = toolRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("工具不存在"));

        if (!tool.getOwnerId().equals(currentUserId)) {
            throw new BadRequestException("只有工具所有者可以完成维修");
        }

        if (tool.getStatus() != ToolStatus.MAINTENANCE) {
            throw new BadRequestException("工具不在维修状态，无法完成维修");
        }

        ToolBox toolBox = toolBoxRepository.findById(tool.getBoxId()).orElse(null);
        if (toolBox != null && !Boolean.TRUE.equals(toolBox.getIsActive())) {
            tool.setStatus(ToolStatus.MAINTENANCE);
        } else {
            tool.setStatus(ToolStatus.AVAILABLE);
        }

        Tool savedTool = toolRepository.save(tool);

        toolLogService.createLogInternal(id, currentUserId, ToolLogAction.REPAIR, request.getDescription());

        return toolResponseMapper.toResponse(savedTool);
    }

    @Transactional
    public void deleteTool(Long id, Long currentUserId) {
        Tool tool = toolRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("工具不存在"));

        if (!tool.getOwnerId().equals(currentUserId)) {
            throw new BadRequestException("无权删除此工具");
        }

        toolRepository.delete(tool);
    }

    @Transactional
    public ToolResponse adminDisableTool(Long id) {
        Tool tool = toolRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("工具不存在"));

        if (tool.getStatus() == ToolStatus.BORROWED) {
            throw new BadRequestException("工具正在借用中，无法禁用");
        }

        tool.setStatus(ToolStatus.DISABLED);
        Tool savedTool = toolRepository.save(tool);
        return toolResponseMapper.toResponse(savedTool);
    }

    @Transactional
    public ToolResponse adminEnableTool(Long id) {
        Tool tool = toolRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("工具不存在"));

        if (tool.getStatus() != ToolStatus.DISABLED) {
            throw new BadRequestException("工具未被禁用");
        }

        ToolBox toolBox = toolBoxRepository.findById(tool.getBoxId()).orElse(null);
        if (toolBox != null && !Boolean.TRUE.equals(toolBox.getIsActive())) {
            tool.setStatus(ToolStatus.MAINTENANCE);
        } else {
            tool.setStatus(ToolStatus.AVAILABLE);
        }

        Tool savedTool = toolRepository.save(tool);
        return toolResponseMapper.toResponse(savedTool);
    }

    public boolean isToolOwner(Long toolId, Long userId) {
        return toolRepository.findById(toolId)
                .map(tool -> tool.getOwnerId().equals(userId))
                .orElse(false);
    }
}
