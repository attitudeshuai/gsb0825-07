package com.toolshare.mapper;

import com.toolshare.dto.tooleview.ToolReviewResponse;
import com.toolshare.entity.ToolReview;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * ToolReview 实体 -> ToolReviewResponse 的统一转换，工具名/评价人名按集合批量加载。
 */
@Component
public class ToolReviewResponseMapper {

    private final ReferenceDataLoader referenceDataLoader;

    public ToolReviewResponseMapper(ReferenceDataLoader referenceDataLoader) {
        this.referenceDataLoader = referenceDataLoader;
    }

    public ToolReviewResponse toResponse(ToolReview review) {
        if (review == null) {
            return null;
        }
        return toResponseList(List.of(review)).get(0);
    }

    public List<ToolReviewResponse> toResponseList(List<ToolReview> reviews) {
        if (reviews == null || reviews.isEmpty()) {
            return new ArrayList<>();
        }

        Map<Long, String> toolNameMap = referenceDataLoader.getToolNames(
                reviews.stream().map(ToolReview::getToolId).collect(Collectors.toList()));
        Map<Long, String> reviewerNameMap = referenceDataLoader.getUserNames(
                reviews.stream().map(ToolReview::getReviewerId).collect(Collectors.toList()));

        List<ToolReviewResponse> responses = new ArrayList<>();
        for (ToolReview review : reviews) {
            if (review == null) {
                continue;
            }
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
}
