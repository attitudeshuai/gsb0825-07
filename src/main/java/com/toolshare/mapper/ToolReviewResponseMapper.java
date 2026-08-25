package com.toolshare.mapper;

import com.toolshare.dto.tooleview.ToolReviewResponse;
import com.toolshare.entity.ToolReview;
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
 * 工具评价实体到 {@link ToolReviewResponse} 的公共转换器。
 */
@Component
public class ToolReviewResponseMapper extends AbstractResponseMapper<ToolReview, ToolReviewResponse> {

    private final ToolRepository toolRepository;
    private final UserRepository userRepository;

    public ToolReviewResponseMapper(ToolRepository toolRepository, UserRepository userRepository) {
        this.toolRepository = toolRepository;
        this.userRepository = userRepository;
    }

    @Override
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
}
