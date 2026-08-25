package com.toolshare.service;

import com.toolshare.dto.PageResponse;
import com.toolshare.dto.tooleview.CreateToolReviewRequest;
import com.toolshare.dto.tooleview.ToolReviewResponse;
import com.toolshare.entity.BorrowRequest;
import com.toolshare.entity.BorrowRequestStatus;
import com.toolshare.entity.ToolReview;
import com.toolshare.exception.BadRequestException;
import com.toolshare.exception.ResourceNotFoundException;
import com.toolshare.mapper.ToolReviewResponseMapper;
import com.toolshare.repository.BorrowRequestRepository;
import com.toolshare.repository.ToolReviewRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ToolReviewService {

    private final ToolReviewRepository toolReviewRepository;
    private final BorrowRequestRepository borrowRequestRepository;
    private final ToolReviewResponseMapper toolReviewResponseMapper;

    public ToolReviewService(ToolReviewRepository toolReviewRepository,
                             BorrowRequestRepository borrowRequestRepository,
                             ToolReviewResponseMapper toolReviewResponseMapper) {
        this.toolReviewRepository = toolReviewRepository;
        this.borrowRequestRepository = borrowRequestRepository;
        this.toolReviewResponseMapper = toolReviewResponseMapper;
    }

    @Transactional
    public ToolReviewResponse createReview(CreateToolReviewRequest request, Long reviewerId) {
        BorrowRequest borrowRequest = borrowRequestRepository.findById(request.getBorrowRequestId())
                .orElseThrow(() -> new ResourceNotFoundException("借用申请不存在"));

        if (!borrowRequest.getRequesterId().equals(reviewerId)) {
            throw new BadRequestException("只有借用人可以评价此工具");
        }

        if (borrowRequest.getStatus() != BorrowRequestStatus.RETURNED) {
            throw new BadRequestException("只能评价已归还的借用申请");
        }

        if (toolReviewRepository.existsByBorrowRequestId(request.getBorrowRequestId())) {
            throw new BadRequestException("该借用申请已评价过，不能重复评价");
        }

        ToolReview review = new ToolReview();
        review.setToolId(borrowRequest.getToolId());
        review.setBorrowRequestId(request.getBorrowRequestId());
        review.setReviewerId(reviewerId);
        review.setRating(request.getRating());
        review.setComment(request.getComment());

        ToolReview savedReview = toolReviewRepository.save(review);
        return toolReviewResponseMapper.toResponse(savedReview);
    }

    public PageResponse<ToolReviewResponse> getReviewsByToolId(Long toolId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<ToolReview> reviewPage = toolReviewRepository.findByToolId(toolId, pageable);
        List<ToolReviewResponse> responseList = toolReviewResponseMapper.toResponseList(reviewPage.getContent());
        return PageResponse.of(responseList, reviewPage.getTotalElements(), reviewPage.getNumber(), reviewPage.getSize());
    }

    public PageResponse<ToolReviewResponse> getMyReviews(Long reviewerId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<ToolReview> reviewPage = toolReviewRepository.findByReviewerId(reviewerId, pageable);
        List<ToolReviewResponse> responseList = toolReviewResponseMapper.toResponseList(reviewPage.getContent());
        return PageResponse.of(responseList, reviewPage.getTotalElements(), reviewPage.getNumber(), reviewPage.getSize());
    }

    public ToolReviewResponse getReviewByBorrowRequestId(Long borrowRequestId) {
        ToolReview review = toolReviewRepository.findByBorrowRequestId(borrowRequestId)
                .orElseThrow(() -> new ResourceNotFoundException("该借用申请暂无评价"));
        return toolReviewResponseMapper.toResponse(review);
    }

    public Double getAverageRatingByToolId(Long toolId) {
        return toolReviewRepository.findAverageRatingByToolId(toolId);
    }

    public Long getReviewCountByToolId(Long toolId) {
        Long count = toolReviewRepository.countByToolId(toolId);
        return count != null ? count : 0L;
    }

    public boolean hasReviewed(Long borrowRequestId) {
        return toolReviewRepository.existsByBorrowRequestId(borrowRequestId);
    }
}
