package com.toolshare.mapper;

import com.toolshare.dto.borrowrequest.BorrowRequestResponse;
import com.toolshare.entity.BorrowRequest;
import com.toolshare.entity.BorrowRequestStatus;
import com.toolshare.repository.ToolReviewRepository;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * BorrowRequest 实体 -> BorrowRequestResponse 的统一转换。
 * 工具名/申请人名/是否已评价均按集合批量加载，逾期与临期状态在同一处计算。
 */
@Component
public class BorrowRequestResponseMapper {

    private final ReferenceDataLoader referenceDataLoader;
    private final ToolReviewRepository toolReviewRepository;

    public BorrowRequestResponseMapper(ReferenceDataLoader referenceDataLoader,
                                       ToolReviewRepository toolReviewRepository) {
        this.referenceDataLoader = referenceDataLoader;
        this.toolReviewRepository = toolReviewRepository;
    }

    public BorrowRequestResponse toResponse(BorrowRequest borrowRequest) {
        if (borrowRequest == null) {
            return null;
        }
        return toResponseList(List.of(borrowRequest)).get(0);
    }

    public List<BorrowRequestResponse> toResponseList(List<BorrowRequest> borrowRequests) {
        if (borrowRequests == null || borrowRequests.isEmpty()) {
            return new ArrayList<>();
        }

        List<Long> borrowRequestIds = borrowRequests.stream()
                .map(BorrowRequest::getId).collect(Collectors.toList());

        Map<Long, String> toolNameMap = referenceDataLoader.getToolNames(
                borrowRequests.stream().map(BorrowRequest::getToolId).collect(Collectors.toList()));
        Map<Long, String> requesterNameMap = referenceDataLoader.getUserNames(
                borrowRequests.stream().map(BorrowRequest::getRequesterId).collect(Collectors.toList()));

        Set<Long> reviewedIds = new HashSet<>(
                toolReviewRepository.findReviewedBorrowRequestIds(borrowRequestIds));

        LocalDate today = LocalDate.now();
        List<BorrowRequestResponse> responses = new ArrayList<>();
        for (BorrowRequest borrowRequest : borrowRequests) {
            BorrowRequestResponse response = new BorrowRequestResponse();
            response.setId(borrowRequest.getId());
            response.setToolId(borrowRequest.getToolId());
            response.setRequesterId(borrowRequest.getRequesterId());
            response.setStartDate(borrowRequest.getStartDate());
            response.setExpectedReturnDate(borrowRequest.getExpectedReturnDate());
            response.setActualReturnDate(borrowRequest.getActualReturnDate());
            response.setStatus(borrowRequest.getStatus());
            response.setRemark(borrowRequest.getRemark());
            response.setCreatedAt(borrowRequest.getCreatedAt());

            response.setToolName(toolNameMap.get(borrowRequest.getToolId()));
            response.setRequesterName(requesterNameMap.get(borrowRequest.getRequesterId()));
            response.setHasReviewed(reviewedIds.contains(borrowRequest.getId()));

            populateOverdueFields(response, borrowRequest, today);

            responses.add(response);
        }
        return responses;
    }

    private void populateOverdueFields(BorrowRequestResponse response, BorrowRequest borrowRequest, LocalDate today) {
        boolean isApproved = borrowRequest.getStatus() == BorrowRequestStatus.APPROVED;
        boolean notReturned = borrowRequest.getActualReturnDate() == null;

        if (isApproved && notReturned) {
            if (borrowRequest.getExpectedReturnDate().isBefore(today)) {
                response.setIsOverdue(true);
                response.setOverdueDays((int) ChronoUnit.DAYS.between(borrowRequest.getExpectedReturnDate(), today));
                response.setIsDueSoon(false);
            } else {
                response.setIsOverdue(false);
                response.setOverdueDays(0);
                long daysUntilDue = ChronoUnit.DAYS.between(today, borrowRequest.getExpectedReturnDate());
                response.setIsDueSoon(daysUntilDue <= 3 && daysUntilDue >= 0);
            }
        } else {
            response.setIsOverdue(false);
            response.setOverdueDays(0);
            response.setIsDueSoon(false);
        }
    }
}
