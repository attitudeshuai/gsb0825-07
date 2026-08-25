package com.toolshare.service.mapper;

import com.toolshare.dto.PageResponse;
import com.toolshare.dto.overduerecord.OverdueRecordResponse;
import com.toolshare.entity.OverdueRecord;
import com.toolshare.repository.ToolRepository;
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
 * OverdueRecord -> OverdueRecordResponse 的统一转换组件。
 * 单条与批量共用同一套字段映射，工具名、借用人姓名按 ID 批量加载，避免 N+1。
 */
@Component
public class OverdueRecordResponseMapper {

    private final ToolRepository toolRepository;
    private final UserRepository userRepository;

    public OverdueRecordResponseMapper(ToolRepository toolRepository, UserRepository userRepository) {
        this.toolRepository = toolRepository;
        this.userRepository = userRepository;
    }

    public OverdueRecordResponse toResponse(OverdueRecord record) {
        return toResponseList(Collections.singletonList(record)).get(0);
    }

    public List<OverdueRecordResponse> toResponseList(List<OverdueRecord> records) {
        if (records == null || records.isEmpty()) {
            return new ArrayList<>();
        }

        Set<Long> toolIds = records.stream().map(OverdueRecord::getToolId).collect(Collectors.toSet());
        Set<Long> requesterIds = records.stream().map(OverdueRecord::getRequesterId).collect(Collectors.toSet());

        Map<Long, String> toolNameMap = new HashMap<>();
        if (!toolIds.isEmpty()) {
            toolRepository.findAllById(toolIds).forEach(t -> toolNameMap.put(t.getId(), t.getName()));
        }
        Map<Long, String> requesterNameMap = new HashMap<>();
        if (!requesterIds.isEmpty()) {
            userRepository.findAllById(requesterIds).forEach(u -> requesterNameMap.put(u.getId(), u.getUsername()));
        }

        List<OverdueRecordResponse> responses = new ArrayList<>();
        for (OverdueRecord record : records) {
            OverdueRecordResponse response = new OverdueRecordResponse();
            response.setId(record.getId());
            response.setBorrowRequestId(record.getBorrowRequestId());
            response.setToolId(record.getToolId());
            response.setToolName(toolNameMap.get(record.getToolId()));
            response.setRequesterId(record.getRequesterId());
            response.setRequesterName(requesterNameMap.get(record.getRequesterId()));
            response.setExpectedReturnDate(record.getExpectedReturnDate());
            response.setOverdueDate(record.getOverdueDate());
            response.setOverdueDays(record.getOverdueDays());
            response.setResolved(record.isResolved());
            response.setResolvedAt(record.getResolvedAt());
            response.setCreatedAt(record.getCreatedAt());
            response.setUpdatedAt(record.getUpdatedAt());
            responses.add(response);
        }
        return responses;
    }

    public PageResponse<OverdueRecordResponse> toPageResponse(Page<OverdueRecord> page) {
        return PageResponse.of(toResponseList(page.getContent()),
                page.getTotalElements(), page.getNumber(), page.getSize());
    }
}
