package com.toolshare.service.mapper;

import com.toolshare.dto.PageResponse;
import com.toolshare.dto.tooleview.ToolReviewResponse;
import com.toolshare.entity.ToolReview;
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
 * ToolReview -> ToolReviewResponse 的统一转换组件。
 * 单条与批量共用同一套字段映射，工具名、评价人名按 ID 批量加载，避免 N+1。
 */
@Component
public class ToolReviewResponseMapper {

    private final ToolRepository toolRepository;
    private final UserRepository userRepository;

    public ToolReviewResponseMapper(ToolRepository toolRepository, UserRepository userRepository) {
        this.toolRepository = toolRepository;
        this.userRepository = userRepository;
    }

    public ToolReviewResponse toResponse(ToolReview review) {
        return toResponseList(Collections.singletonList(review)).get(0);
    }

    public List<ToolReviewResponse> toResponseList(List<ToolReview> reviews) {
        if (reviews == null || reviews.isEmpty()) {
            return new ArrayList<>();
        }

        Set<Long> toolIds = reviews.stream().map(ToolReview::getToolId).collect(Collectors.toSet());
        Set<Long> reviewerIds = reviews.stream().map(ToolReview::getReviewerId).collect(Collectors.toSet());

        Map<Long, String> toolNameMap = new HashMap<>();
        if (!toolIds.isEmpty()) {
            toolRepository.findAllById(toolIds).forEach(t -> toolNameMap.put(t.getId(), t.getName()));
        }
        Map<Long, String> reviewerNameMap = new HashMap<>();
        if (!reviewerIds.isEmpty()) {
            userRepository.findAllById(reviewerIds).forEach(u -> reviewerNameMap.put(u.getId(), u.getUsername()));
        }

        List<ToolReviewResponse> responses = new ArrayList<>();
        for (ToolReview review : reviews) {
            ToolReviewResponse response = new ToolReviewResponse();
            response.setId(review.getId());
            response.setToolId(review.getToolId());
            response.setBorrowRequestId(review.getBorrowRequestId());
            response.setReviewerId(review.getReviewerId());
            response.setRating(review.getRating());
            response.setComment(review.getComment());
            response.setCreatedAt(review.getCreatedAt());
            response.setToolName(toolNameMap.get(review.getToolId()));
            response.setReviewerName(reviewerNameMap.get(review.getReviewerId()));
            responses.add(response);
        }
        return responses;
    }

    public PageResponse<ToolReviewResponse> toPageResponse(Page<ToolReview> page) {
        return PageResponse.of(toResponseList(page.getContent()),
                page.getTotalElements(), page.getNumber(), page.getSize());
    }
}
