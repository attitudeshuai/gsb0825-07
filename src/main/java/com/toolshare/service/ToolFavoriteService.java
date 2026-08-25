package com.toolshare.service;

import com.toolshare.dto.PageResponse;
import com.toolshare.dto.toolfavorite.ToolFavoriteResponse;
import com.toolshare.entity.ToolFavorite;
import com.toolshare.exception.BadRequestException;
import com.toolshare.exception.ResourceNotFoundException;
import com.toolshare.mapper.ToolFavoriteResponseMapper;
import com.toolshare.repository.ToolFavoriteRepository;
import com.toolshare.repository.ToolRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class ToolFavoriteService {

    private final ToolFavoriteRepository toolFavoriteRepository;
    private final ToolRepository toolRepository;
    private final ToolFavoriteResponseMapper toolFavoriteResponseMapper;

    public ToolFavoriteService(ToolFavoriteRepository toolFavoriteRepository,
                               ToolRepository toolRepository,
                               ToolFavoriteResponseMapper toolFavoriteResponseMapper) {
        this.toolFavoriteRepository = toolFavoriteRepository;
        this.toolRepository = toolRepository;
        this.toolFavoriteResponseMapper = toolFavoriteResponseMapper;
    }

    @Transactional
    public ToolFavoriteResponse addFavorite(Long userId, Long toolId) {
        if (!toolRepository.existsById(toolId)) {
            throw new ResourceNotFoundException("工具不存在");
        }

        if (toolFavoriteRepository.existsByUserIdAndToolId(userId, toolId)) {
            throw new BadRequestException("该工具已收藏");
        }

        ToolFavorite favorite = new ToolFavorite();
        favorite.setUserId(userId);
        favorite.setToolId(toolId);

        ToolFavorite savedFavorite = toolFavoriteRepository.save(favorite);
        return toolFavoriteResponseMapper.toResponse(savedFavorite);
    }

    @Transactional
    public void removeFavorite(Long userId, Long toolId) {
        if (!toolFavoriteRepository.existsByUserIdAndToolId(userId, toolId)) {
            throw new ResourceNotFoundException("未收藏该工具");
        }
        toolFavoriteRepository.deleteByUserIdAndToolId(userId, toolId);
    }

    public boolean isFavorited(Long userId, Long toolId) {
        return toolFavoriteRepository.existsByUserIdAndToolId(userId, toolId);
    }

    public PageResponse<ToolFavoriteResponse> getMyFavorites(Long userId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<ToolFavorite> favoritePage = toolFavoriteRepository.findByUserId(userId, pageable);
        List<ToolFavoriteResponse> responseList = toolFavoriteResponseMapper.toResponseList(favoritePage.getContent());
        return PageResponse.of(responseList, favoritePage.getTotalElements(), favoritePage.getNumber(), favoritePage.getSize());
    }

    public long getFavoriteCountByToolId(Long toolId) {
        return toolFavoriteRepository.countByToolId(toolId);
    }

    public Map<Long, Boolean> getFavoriteStatusMap(Long userId, List<Long> toolIds) {
        if (userId == null || toolIds == null || toolIds.isEmpty()) {
            return new HashMap<>();
        }
        List<Long> favoritedIds = toolFavoriteRepository.findFavoritedToolIdsByUserIdAndToolIds(userId, toolIds);
        Set<Long> favoritedSet = new HashSet<>(favoritedIds);
        Map<Long, Boolean> result = new HashMap<>();
        for (Long id : toolIds) {
            result.put(id, favoritedSet.contains(id));
        }
        return result;
    }
}
