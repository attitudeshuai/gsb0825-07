package com.toolshare.mapper;

import com.toolshare.dto.borrowrequest.BorrowRequestResponse;
import com.toolshare.entity.BorrowRequest;
import com.toolshare.entity.BorrowRequestStatus;
import com.toolshare.repository.ToolRepository;
import com.toolshare.repository.UserRepository;
import com.toolshare.service.ToolReviewService;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 借用申请实体到 {@link BorrowRequestResponse} 的公共转换器。
 *
 * <p>字段拷贝、关联数据（工具名、申请人名、是否已评价）以及逾期字段的计算集中一次实现，
 * 关联数据批量加载，单条转换复用批量逻辑。</p>
 */
@Component
public class BorrowRequestResponseMapper extends AbstractResponseMapper<BorrowRequest, BorrowRequestResponse> {

    private final ToolRepository toolRepository;
    private final UserRepository userRepository;
    private final ToolReviewService toolReviewService;

    public BorrowRequestResponseMapper(ToolRepository toolRepository,
                                       UserRepository userRepository,
                                       ToolReviewService toolReviewService) {
        this.toolRepository = toolRepository;
        this.userRepository = userRepository;
        this.toolReviewService = toolReviewService;
    }

    @Override
    public List<BorrowRequestResponse> toResponseList(List<BorrowRequest> borrowRequests) {
        if (borrowRequests == null || borrowRequests.isEmpty()) {
            return new ArrayList<>();
        }

        Set<Long> borrowRequestIds = borrowRequests.stream().map(BorrowRequest::getId).collect(Collectors.toSet());
        Set<Long> toolIds = borrowRequests.stream().map(BorrowRequest::getToolId).collect(Collectors.toSet());
        Set<Long> requesterIds = borrowRequests.stream().map(BorrowRequest::getRequesterId).collect(Collectors.toSet());

        Map<Long, Boolean> hasReviewedMap = toolReviewService.getHasReviewedMapByBorrowRequestIds(new ArrayList<>(borrowRequestIds));
        Map<Long, String> toolNameMap = new HashMap<>();
        if (!toolIds.isEmpty()) {
            toolRepository.findAllById(toolIds).forEach(t -> toolNameMap.put(t.getId(), t.getName()));
        }
        Map<Long, String> requesterNameMap = new HashMap<>();
        if (!requesterIds.isEmpty()) {
            userRepository.findAllById(requesterIds).forEach(u -> requesterNameMap.put(u.getId(), u.getUsername()));
        }

        List<BorrowRequestResponse> responses = new ArrayList<>();
        LocalDate today = LocalDate.now();
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
            response.setHasReviewed(hasReviewedMap.getOrDefault(borrowRequest.getId(), false));

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
