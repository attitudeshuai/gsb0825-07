package com.toolshare.mapper;

import com.toolshare.dto.overduerecord.OverdueRecordResponse;
import com.toolshare.entity.OverdueRecord;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * OverdueRecord 实体 -> OverdueRecordResponse 的统一转换，
 * 工具名/借用人名按集合批量加载。
 */
@Component
public class OverdueRecordResponseMapper {

    private final ReferenceDataLoader referenceDataLoader;

    public OverdueRecordResponseMapper(ReferenceDataLoader referenceDataLoader) {
        this.referenceDataLoader = referenceDataLoader;
    }

    public OverdueRecordResponse toResponse(OverdueRecord record) {
        if (record == null) {
            return null;
        }
        return toResponseList(List.of(record)).get(0);
    }

    public List<OverdueRecordResponse> toResponseList(List<OverdueRecord> records) {
        if (records == null || records.isEmpty()) {
            return new ArrayList<>();
        }

        Map<Long, String> toolNameMap = referenceDataLoader.getToolNames(
                records.stream().map(OverdueRecord::getToolId).collect(Collectors.toList()));
        Map<Long, String> requesterNameMap = referenceDataLoader.getUserNames(
                records.stream().map(OverdueRecord::getRequesterId).collect(Collectors.toList()));

        List<OverdueRecordResponse> responses = new ArrayList<>();
        for (OverdueRecord record : records) {
            if (record == null) {
                continue;
            }
            OverdueRecordResponse response = new OverdueRecordResponse();
            response.setId(record.getId());
            response.setBorrowRequestId(record.getBorrowRequestId());
            response.setToolId(record.getToolId());
            response.setRequesterId(record.getRequesterId());
            response.setExpectedReturnDate(record.getExpectedReturnDate());
            response.setOverdueDate(record.getOverdueDate());
            response.setOverdueDays(record.getOverdueDays());
            response.setResolved(record.isResolved());
            response.setResolvedAt(record.getResolvedAt());
            response.setCreatedAt(record.getCreatedAt());
            response.setUpdatedAt(record.getUpdatedAt());

            response.setToolName(toolNameMap.get(record.getToolId()));
            response.setRequesterName(requesterNameMap.get(record.getRequesterId()));

            responses.add(response);
        }
        return responses;
    }
}
