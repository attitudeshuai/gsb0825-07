package com.toolshare.mapper;

import com.toolshare.dto.helppost.HelpResponseResponse;
import com.toolshare.entity.HelpPost;
import com.toolshare.entity.HelpResponse;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * HelpResponse 实体 -> HelpResponseResponse 的统一转换。
 * 响应者名、所属求助帖标题均按集合批量加载。
 */
@Component
public class HelpResponseResponseMapper {

    private final ReferenceDataLoader referenceDataLoader;

    public HelpResponseResponseMapper(ReferenceDataLoader referenceDataLoader) {
        this.referenceDataLoader = referenceDataLoader;
    }

    public HelpResponseResponse toResponse(HelpResponse helpResponse) {
        if (helpResponse == null) {
            return null;
        }
        return toResponseList(List.of(helpResponse)).get(0);
    }

    public List<HelpResponseResponse> toResponseList(List<HelpResponse> helpResponses) {
        if (helpResponses == null || helpResponses.isEmpty()) {
            return new ArrayList<>();
        }

        List<HelpResponse> validResponses = helpResponses.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        if (validResponses.isEmpty()) {
            return new ArrayList<>();
        }

        Map<Long, String> responderNameMap = referenceDataLoader.getUserNames(
                validResponses.stream().map(HelpResponse::getResponderId).collect(Collectors.toList()));

        List<Long> helpPostIds = validResponses.stream()
                .map(HelpResponse::getHelpPostId)
                .distinct()
                .collect(Collectors.toList());
        Map<Long, HelpPost> helpPostMap = referenceDataLoader.getHelpPosts(helpPostIds);

        List<HelpResponseResponse> responses = new ArrayList<>();
        for (HelpResponse helpResponse : validResponses) {
            HelpResponseResponse response = new HelpResponseResponse();
            response.setId(helpResponse.getId());
            response.setHelpPostId(helpResponse.getHelpPostId());
            response.setResponderId(helpResponse.getResponderId());
            response.setMessage(helpResponse.getMessage());
            response.setContactInfo(helpResponse.getContactInfo());
            response.setAccepted(helpResponse.isAccepted());
            response.setCreatedAt(helpResponse.getCreatedAt());

            response.setResponderName(responderNameMap.get(helpResponse.getResponderId()));
            HelpPost helpPost = helpPostMap.get(helpResponse.getHelpPostId());
            if (helpPost != null) {
                response.setHelpPostTitle(helpPost.getTitle());
            }

            responses.add(response);
        }
        return responses;
    }
}
