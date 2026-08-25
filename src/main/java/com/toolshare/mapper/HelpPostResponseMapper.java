package com.toolshare.mapper;

import com.toolshare.dto.helppost.HelpPostResponse;
import com.toolshare.entity.HelpPost;
import com.toolshare.repository.HelpPostRepository;
import com.toolshare.repository.UserRepository;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 求助帖实体到 {@link HelpPostResponse} 的公共转换器。
 *
 * <p>响应数量、发布者名、被接受响应者名统一批量加载，单条转换复用批量逻辑。</p>
 */
@Component
public class HelpPostResponseMapper extends AbstractResponseMapper<HelpPost, HelpPostResponse> {

    private final HelpPostRepository helpPostRepository;
    private final UserRepository userRepository;

    public HelpPostResponseMapper(HelpPostRepository helpPostRepository, UserRepository userRepository) {
        this.helpPostRepository = helpPostRepository;
        this.userRepository = userRepository;
    }

    @Override
    public List<HelpPostResponse> toResponseList(List<HelpPost> helpPosts) {
        if (helpPosts == null || helpPosts.isEmpty()) {
            return new ArrayList<>();
        }

        List<Long> postIds = helpPosts.stream().map(HelpPost::getId).collect(Collectors.toList());
        Map<Long, Long> responseCountMap = helpPostRepository.findResponseCountMapByHelpPostIds(postIds);

        Set<Long> userIds = new HashSet<>();
        for (HelpPost post : helpPosts) {
            userIds.add(post.getPosterId());
            if (post.getAcceptedResponderId() != null) {
                userIds.add(post.getAcceptedResponderId());
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

            Long count = responseCountMap.get(helpPost.getId());
            response.setResponseCount(count != null ? count.intValue() : 0);

            response.setPosterName(userNameMap.get(helpPost.getPosterId()));
            if (helpPost.getAcceptedResponderId() != null) {
                response.setAcceptedResponderName(userNameMap.get(helpPost.getAcceptedResponderId()));
            }

            responses.add(response);
        }
        return responses;
    }
}
