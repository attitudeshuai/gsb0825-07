package com.toolshare.mapper;

import com.toolshare.dto.overduerecord.OverdueRecordResponse;
import com.toolshare.entity.OverdueRecord;
import com.toolshare.repository.ToolRepository;
import com.toolshare.repository.UserRepository;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 逾期记录实体到 {@link OverdueRecordResponse} 的公共转换器。
 */
@Component
public class OverdueRecordResponseMapper extends AbstractResponseMapper<OverdueRecord, OverdueRecordResponse> {

    private final ToolRepository toolRepository;
    private final UserRepository userRepository;

    public OverdueRecordResponseMapper(ToolRepository toolRepository, UserRepository userRepository) {
        this.toolRepository = toolRepository;
        this.userRepository = userRepository;
    }

    @Override
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
}
