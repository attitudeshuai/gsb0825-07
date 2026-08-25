package com.toolshare.service;

import com.toolshare.dto.PageResponse;
import com.toolshare.dto.toollog.CreateToolLogRequest;
import com.toolshare.dto.toollog.ToolLogResponse;
import com.toolshare.dto.toollog.UpdateToolLogRequest;
import com.toolshare.entity.ToolLog;
import com.toolshare.entity.ToolLogAction;
import com.toolshare.exception.BadRequestException;
import com.toolshare.exception.ResourceNotFoundException;
import com.toolshare.mapper.ReferenceDataLoader;
import com.toolshare.mapper.ToolLogResponseMapper;
import com.toolshare.repository.ToolLogRepository;
import com.toolshare.repository.ToolRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Service
public class ToolLogService {

    private final ToolLogRepository toolLogRepository;
    private final ToolRepository toolRepository;
    private final ToolLogResponseMapper toolLogResponseMapper;
    private final ReferenceDataLoader referenceDataLoader;

    public ToolLogService(ToolLogRepository toolLogRepository,
                          ToolRepository toolRepository,
                          ToolLogResponseMapper toolLogResponseMapper,
                          ReferenceDataLoader referenceDataLoader) {
        this.toolLogRepository = toolLogRepository;
        this.toolRepository = toolRepository;
        this.toolLogResponseMapper = toolLogResponseMapper;
        this.referenceDataLoader = referenceDataLoader;
    }

    public PageResponse<ToolLogResponse> getAllToolLogs(Long toolId, Long userId, ToolLogAction action,
                                                        LocalDateTime startTime, LocalDateTime endTime,
                                                        int page, int size, String sortBy, String sortDir) {
        Sort sort = sortDir.equalsIgnoreCase("desc") ? Sort.by(sortBy).descending() : Sort.by(sortBy).ascending();
        Pageable pageable = PageRequest.of(page, size, sort);

        Page<ToolLog> logPage = toolLogRepository.search(toolId, userId, action, startTime, endTime, pageable);
        List<ToolLogResponse> responseList = toolLogResponseMapper.toResponseList(logPage.getContent());

        return PageResponse.of(responseList, logPage.getTotalElements(), logPage.getNumber(), logPage.getSize());
    }

    public ToolLogResponse getToolLogById(Long id) {
        ToolLog toolLog = toolLogRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("使用日志不存在"));
        return toolLogResponseMapper.toResponse(toolLog);
    }

    public PageResponse<ToolLogResponse> getMyToolLogs(Long userId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<ToolLog> logPage = toolLogRepository.findByUserId(userId, pageable);
        List<ToolLogResponse> responseList = toolLogResponseMapper.toResponseList(logPage.getContent());
        return PageResponse.of(responseList, logPage.getTotalElements(), logPage.getNumber(), logPage.getSize());
    }

    @Transactional
    public ToolLogResponse createToolLog(CreateToolLogRequest request, Long userId) {
        if (!toolRepository.existsById(request.getToolId())) {
            throw new ResourceNotFoundException("工具不存在");
        }

        ToolLog toolLog = new ToolLog();
        toolLog.setToolId(request.getToolId());
        toolLog.setUserId(userId);
        toolLog.setAction(request.getAction());
        toolLog.setDescription(request.getDescription());

        ToolLog savedLog = toolLogRepository.save(toolLog);
        return toolLogResponseMapper.toResponse(savedLog);
    }

    @Transactional
    public void createLogInternal(Long toolId, Long userId, ToolLogAction action, String description) {
        ToolLog toolLog = new ToolLog();
        toolLog.setToolId(toolId);
        toolLog.setUserId(userId);
        toolLog.setAction(action);
        toolLog.setDescription(description);
        toolLogRepository.save(toolLog);
    }

    @Transactional
    public ToolLogResponse updateToolLog(Long id, UpdateToolLogRequest request, Long currentUserId) {
        ToolLog toolLog = toolLogRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("使用日志不存在"));

        if (!toolLog.getUserId().equals(currentUserId)) {
            throw new BadRequestException("无权修改此日志");
        }

        if (request.getAction() != null) {
            toolLog.setAction(request.getAction());
        }
        if (request.getDescription() != null) {
            toolLog.setDescription(request.getDescription());
        }

        ToolLog savedLog = toolLogRepository.save(toolLog);
        return toolLogResponseMapper.toResponse(savedLog);
    }

    @Transactional
    public void deleteToolLog(Long id, Long currentUserId) {
        ToolLog toolLog = toolLogRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("使用日志不存在"));

        if (!toolLog.getUserId().equals(currentUserId)) {
            throw new BadRequestException("无权删除此日志");
        }

        toolLogRepository.delete(toolLog);
    }

    public byte[] exportToolLogsToCsv(Long toolId, Long userId, ToolLogAction action,
                                       LocalDateTime startTime, LocalDateTime endTime) {
        List<ToolLog> logs = toolLogRepository.searchForExport(toolId, userId, action, startTime, endTime);

        Map<Long, String> toolNameMap = referenceDataLoader.getToolNames(
                logs.stream().map(ToolLog::getToolId).collect(java.util.stream.Collectors.toList()));
        Map<Long, String> userNameMap = referenceDataLoader.getUserNames(
                logs.stream().map(ToolLog::getUserId).collect(java.util.stream.Collectors.toList()));

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (PrintWriter writer = new PrintWriter(new OutputStreamWriter(baos, StandardCharsets.UTF_8))) {
            writer.print('\uFEFF');

            writer.println("日志ID,工具ID,工具名称,用户ID,用户名,操作类型,描述,创建时间");

            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            for (ToolLog log : logs) {
                String toolName = toolNameMap.getOrDefault(log.getToolId(), "");
                String userName = userNameMap.getOrDefault(log.getUserId(), "");

                writer.printf("%d,%d,%s,%d,%s,%s,%s,%s%n",
                        log.getId(),
                        log.getToolId(),
                        escapeCsv(toolName),
                        log.getUserId(),
                        escapeCsv(userName),
                        escapeCsv(getActionDisplayName(log.getAction())),
                        escapeCsv(log.getDescription() != null ? log.getDescription() : ""),
                        log.getCreatedAt() != null ? log.getCreatedAt().format(formatter) : "");
            }

            writer.flush();
        }

        return baos.toByteArray();
    }

    private String escapeCsv(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private String getActionDisplayName(ToolLogAction action) {
        if (action == null) {
            return "";
        }
        return switch (action) {
            case BORROW -> "借用";
            case RETURN -> "归还";
            case REPORT -> "报修";
            case REPAIR -> "维修";
            case MAINTENANCE -> "保养";
        };
    }
}
