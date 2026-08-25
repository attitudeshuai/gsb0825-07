package com.toolshare.mapper;

import com.toolshare.dto.tool.ToolResponse;
import com.toolshare.entity.Tool;
import com.toolshare.repository.BorrowRequestRepository;
import com.toolshare.repository.ToolFavoriteRepository;
import com.toolshare.repository.ToolReviewRepository;
import com.toolshare.util.SecurityUtil;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Tool 实体 -> ToolResponse 的统一转换。
 * 单条与批量共用同一份映射逻辑，关联数据（工具箱名、所有者名、评分、收藏、借用次数）
 * 全部按 ID 集合批量加载，避免 N+1 查询。
 */
@Component
public class ToolResponseMapper {

    private static final long BORROW_WEIGHT = 2;

    private final ReferenceDataLoader referenceDataLoader;
    private final ToolReviewRepository toolReviewRepository;
    private final ToolFavoriteRepository toolFavoriteRepository;
    private final BorrowRequestRepository borrowRequestRepository;

    public ToolResponseMapper(ReferenceDataLoader referenceDataLoader,
                              ToolReviewRepository toolReviewRepository,
                              ToolFavoriteRepository toolFavoriteRepository,
                              BorrowRequestRepository borrowRequestRepository) {
        this.referenceDataLoader = referenceDataLoader;
        this.toolReviewRepository = toolReviewRepository;
        this.toolFavoriteRepository = toolFavoriteRepository;
        this.borrowRequestRepository = borrowRequestRepository;
    }

    public ToolResponse toResponse(Tool tool) {
        if (tool == null) {
            return null;
        }
        return toResponseList(List.of(tool)).get(0);
    }

    public List<ToolResponse> toResponseList(List<Tool> tools) {
        if (tools == null || tools.isEmpty()) {
            return new ArrayList<>();
        }

        List<Long> toolIds = tools.stream().map(Tool::getId).collect(Collectors.toList());

        Map<Long, String> boxNameMap = referenceDataLoader.getToolBoxNames(
                tools.stream().map(Tool::getBoxId).collect(Collectors.toList()));
        Map<Long, String> ownerNameMap = referenceDataLoader.getUserNames(
                tools.stream().map(Tool::getOwnerId).collect(Collectors.toList()));

        Map<Long, Double> averageRatingMap = toolReviewRepository.findAverageRatingMapByToolIds(toolIds);
        Map<Long, Long> reviewCountMap = toolReviewRepository.findReviewCountMapByToolIds(toolIds);

        Long currentUserId = SecurityUtil.getCurrentUserId();
        Set<Long> favoritedToolIds = Collections.emptySet();
        if (currentUserId != null) {
            favoritedToolIds = new HashSet<>(
                    toolFavoriteRepository.findFavoritedToolIdsByUserIdAndToolIds(currentUserId, toolIds));
        }

        Map<Long, Long> favoriteCountMap = countRowsToMap(
                toolFavoriteRepository.countByToolIdsGrouped(toolIds));
        Map<Long, Long> borrowCountMap = countRowsToMap(
                borrowRequestRepository.countByToolIdsGrouped(toolIds));

        List<ToolResponse> responses = new ArrayList<>();
        for (Tool tool : tools) {
            if (tool == null) {
                continue;
            }
            ToolResponse response = new ToolResponse();
            response.setId(tool.getId());
            response.setBoxId(tool.getBoxId());
            response.setName(tool.getName());
            response.setCategory(tool.getCategory());
            response.setStatus(tool.getStatus());
            response.setDescription(tool.getDescription());
            response.setImage(tool.getImage());
            response.setPurchaseDate(tool.getPurchaseDate());
            response.setOwnerId(tool.getOwnerId());
            response.setCreatedAt(tool.getCreatedAt());
            response.setMaxBorrowDays(tool.getMaxBorrowDays());

            response.setBoxName(boxNameMap.get(tool.getBoxId()));
            response.setOwnerName(ownerNameMap.get(tool.getOwnerId()));
            response.setAverageRating(averageRatingMap.get(tool.getId()));
            response.setReviewCount(reviewCountMap.getOrDefault(tool.getId(), 0L));
            response.setIsFavorited(favoritedToolIds.contains(tool.getId()));

            long favoriteCount = favoriteCountMap.getOrDefault(tool.getId(), 0L);
            long borrowCount = borrowCountMap.getOrDefault(tool.getId(), 0L);
            response.setFavoriteCount(favoriteCount);
            response.setBorrowCount(borrowCount);
            response.setHotRankScore(borrowCount * BORROW_WEIGHT + favoriteCount);

            responses.add(response);
        }
        return responses;
    }

    private static Map<Long, Long> countRowsToMap(List<Object[]> rows) {
        if (rows == null || rows.isEmpty()) {
            return Collections.emptyMap();
        }
        return rows.stream()
                .collect(Collectors.toMap(
                        row -> ((Number) row[0]).longValue(),
                        row -> ((Number) row[1]).longValue(),
                        (a, b) -> a));
    }
}
