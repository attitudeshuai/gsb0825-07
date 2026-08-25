package com.toolshare.service.mapper;

import com.toolshare.dto.PageResponse;
import com.toolshare.dto.helppost.HelpPostResponse;
import com.toolshare.entity.HelpPost;
import com.toolshare.repository.HelpPostRepository;
import com.toolshare.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * HelpPost -> HelpPostResponse 的统一转换组件。
 * 单条与批量共用同一套字段映射，响应数、发布者/被接受响应者姓名按 ID 批量加载，避免 N+1。
 */
@Component
public class HelpPostResponseMapper {

    private final HelpPostRepository helpPostRepository;
    private final UserRepository userRepository;

    public HelpPostResponseMapper(HelpPostRepository helpPostRepository, UserRepository userRepository) {
        this.helpPostRepository = helpPostRepository;
        this.userRepository = userRepository;
    }

    public HelpPostResponse toResponse(HelpPost helpPost) {
        return toResponseList(Collections.singletonList(helpPost)).get(0);
    }

    public List<HelpPostResponse> toResponseList(List<HelpPost> helpPosts) {
        if (helpPosts == null || helpPosts.isEmpty()) {
            return new ArrayList<>();
        }

        List<Long> helpPostIds = helpPosts.stream().map(HelpPost::getId).collect(Collectors.toList());
        Map<Long, Long> responseCountMap = helpPostRepository.findResponseCountMapByHelpPostIds(helpPostIds);

        Set<Long> userIds = new HashSet<>();
        for (HelpPost helpPost : helpPosts) {
            userIds.add(helpPost.getPosterId());
            if (helpPost.getAcceptedResponderId() != null) {
                userIds.add(helpPost.getAcceptedResponderId());
            }
        }
        Map<Long, String> userNameMap = new HashMap<>();
        if (!userIds.isEmpty()) {
            userRepository.findAllById(userIds).forEach(u -> userNameMap.put(u.getId(), u.getUsername()));
        }

        List<HelpPostResponse> responses = new ArrayList<>();
        for (HelpPost helpPost : helpPosts) {
            HelpPostResponse response = new HelpPostResponse();
            response.setId(helpPost.getId());
            response.setPosterId(helpPost.getPosterId());
            response.setTitle(helpPost.getTitle());
            response.setContent(helpPost.getContent());
            response.setCategory(helpPost.getCategory());
            response.setStatus(helpPost.getStatus());
            response.setLocation(helpPost.getLocation());
            response.setDeadline(helpPost.getDeadline());
            response.setAcceptedResponderId(helpPost.getAcceptedResponderId());
            response.setCreatedAt(helpPost.getCreatedAt());
            response.setUpdatedAt(helpPost.getUpdatedAt());

            response.setResponseCount(responseCountMap.getOrDefault(helpPost.getId(), 0L).intValue());
            response.setPosterName(userNameMap.get(helpPost.getPosterId()));
            if (helpPost.getAcceptedResponderId() != null) {
                response.setAcceptedResponderName(userNameMap.get(helpPost.getAcceptedResponderId()));
            }

            responses.add(response);
        }
        return responses;
    }

    public PageResponse<HelpPostResponse> toPageResponse(Page<HelpPost> page) {
        return PageResponse.of(toResponseList(page.getContent()),
                page.getTotalElements(), page.getNumber(), page.getSize());
    }
}
