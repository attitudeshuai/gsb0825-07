package com.toolshare.mapper;

import com.toolshare.dto.helppost.HelpPostResponse;
import com.toolshare.entity.HelpPost;
import com.toolshare.repository.HelpPostRepository;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * HelpPost 实体 -> HelpPostResponse 的统一转换。
 * 发布者名/被采纳响应者名、响应数量均按集合批量加载。
 */
@Component
public class HelpPostResponseMapper {

    private final ReferenceDataLoader referenceDataLoader;
    private final HelpPostRepository helpPostRepository;

    public HelpPostResponseMapper(ReferenceDataLoader referenceDataLoader,
                                  HelpPostRepository helpPostRepository) {
        this.referenceDataLoader = referenceDataLoader;
        this.helpPostRepository = helpPostRepository;
    }

    public HelpPostResponse toResponse(HelpPost helpPost) {
        if (helpPost == null) {
            return null;
        }
        return toResponseList(List.of(helpPost)).get(0);
    }

    public List<HelpPostResponse> toResponseList(List<HelpPost> helpPosts) {
        if (helpPosts == null || helpPosts.isEmpty()) {
            return new ArrayList<>();
        }

        List<HelpPost> validPosts = helpPosts.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        if (validPosts.isEmpty()) {
            return new ArrayList<>();
        }

        List<Long> postIds = validPosts.stream().map(HelpPost::getId).collect(Collectors.toList());

        Set<Long> userIds = validPosts.stream().map(HelpPost::getPosterId).collect(Collectors.toSet());
        validPosts.stream()
                .map(HelpPost::getAcceptedResponderId)
                .filter(Objects::nonNull)
                .forEach(userIds::add);
        Map<Long, String> userNameMap = referenceDataLoader.getUserNames(userIds);

        Map<Long, Long> responseCountMap = postIds.isEmpty()
                ? Collections.emptyMap()
                : helpPostRepository.findResponseCountMapByHelpPostIds(postIds);

        List<HelpPostResponse> responses = new ArrayList<>();
        for (HelpPost helpPost : validPosts) {
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

            Long responseCount = responseCountMap.get(helpPost.getId());
            response.setResponseCount(responseCount != null ? responseCount.intValue() : 0);
            response.setPosterName(userNameMap.get(helpPost.getPosterId()));
            if (helpPost.getAcceptedResponderId() != null) {
                response.setAcceptedResponderName(userNameMap.get(helpPost.getAcceptedResponderId()));
            }

            responses.add(response);
        }
        return responses;
    }
}
