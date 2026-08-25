package com.toolshare.mapper;

import com.toolshare.dto.tool.ToolResponse;
import com.toolshare.entity.Tool;
import com.toolshare.repository.ToolBoxRepository;
import com.toolshare.repository.ToolFavoriteRepository;
import com.toolshare.repository.UserRepository;
import com.toolshare.service.StatsService;
import com.toolshare.service.ToolReviewService;
import com.toolshare.util.SecurityUtil;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 工具实体到 {@link ToolResponse} 的公共转换器。
 *
 * <p>字段拷贝与关联数据（工具箱名、拥有者名、评分、收藏、借用统计）在此集中一次实现，
 * 关联数据一律批量加载，单条转换复用批量逻辑，避免重复映射与逐字段 N+1 查询。</p>
 */
@Component
public class ToolResponseMapper extends AbstractResponseMapper<Tool, ToolResponse> {

    private final ToolBoxRepository toolBoxRepository;
    private final UserRepository userRepository;
    private final ToolReviewService toolReviewService;
    private final ToolFavoriteRepository toolFavoriteRepository;
    private final StatsService statsService;

    public ToolResponseMapper(ToolBoxRepository toolBoxRepository,
                              UserRepository userRepository,
                              ToolReviewService toolReviewService,
                              ToolFavoriteRepository toolFavoriteRepository,
                              StatsService statsService) {
        this.toolBoxRepository = toolBoxRepository;
        this.userRepository = userRepository;
        this.toolReviewService = toolReviewService;
        this.toolFavoriteRepository = toolFavoriteRepository;
        this.statsService = statsService;
    }

    @Override
    public List<ToolResponse> toResponseList(List<Tool> tools) {
        if (tools == null || tools.isEmpty()) {
            return new ArrayList<>();
        }

        Set<Long> toolIds = tools.stream().map(Tool::getId).collect(Collectors.toSet());
        Set<Long> boxIds = tools.stream().map(Tool::getBoxId).collect(Collectors.toSet());
        Set<Long> ownerIds = tools.stream().map(Tool::getOwnerId).collect(Collectors.toSet());

        Map<Long, Double> averageRatingMap = toolReviewService.getAverageRatingMapByToolIds(new ArrayList<>(toolIds));
        Map<Long, Long> reviewCountMap = toolReviewService.getReviewCountMapByToolIds(new ArrayList<>(toolIds));
        Map<Long, String> boxNameMap = new HashMap<>();
        if (!boxIds.isEmpty()) {
            toolBoxRepository.findAllById(boxIds).forEach(tb -> boxNameMap.put(tb.getId(), tb.getName()));
        }
        Map<Long, String> ownerNameMap = new HashMap<>();
        if (!ownerIds.isEmpty()) {
            userRepository.findAllById(ownerIds).forEach(u -> ownerNameMap.put(u.getId(), u.getUsername()));
        }

        Long currentUserId = SecurityUtil.getCurrentUserId();
        Set<Long> favoritedToolIds = new HashSet<>();
        if (currentUserId != null && !toolIds.isEmpty()) {
            favoritedToolIds = new HashSet<>(toolFavoriteRepository.findFavoritedToolIdsByUserIdAndToolIds(currentUserId, new ArrayList<>(toolIds)));
        }

        Map<Long, Long> favoriteCountMap = new HashMap<>();
        if (!toolIds.isEmpty()) {
            List<Object[]> favCounts = toolFavoriteRepository.countByToolIdsGrouped(new ArrayList<>(toolIds));
            favoriteCountMap = favCounts.stream()
                    .collect(Collectors.toMap(
                            arr -> ((Number) arr[0]).longValue(),
                            arr -> ((Number) arr[1]).longValue()
                    ));
        }

        Map<Long, Long> borrowCountMap = statsService.getBorrowCountMap(new ArrayList<>(toolIds));

        List<ToolResponse> responses = new ArrayList<>();
        for (Tool tool : tools) {
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
            response.setReviewCount(reviewCountMap.get(tool.getId()));
            response.setIsFavorited(currentUserId != null && favoritedToolIds.contains(tool.getId()));

            Long favoriteCount = favoriteCountMap.getOrDefault(tool.getId(), 0L);
            Long borrowCount = borrowCountMap.getOrDefault(tool.getId(), 0L);
            response.setFavoriteCount(favoriteCount);
            response.setBorrowCount(borrowCount);
            response.setHotRankScore(borrowCount * 2 + favoriteCount);

            responses.add(response);
        }
        return responses;
    }
}
