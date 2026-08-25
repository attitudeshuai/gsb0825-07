package com.toolshare.mapper;

import com.toolshare.dto.helppost.HelpResponseResponse;
import com.toolshare.entity.HelpResponse;
import com.toolshare.repository.HelpPostRepository;
import com.toolshare.repository.UserRepository;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 求助响应实体到 {@link HelpResponseResponse} 的公共转换器。
 */
@Component
public class HelpResponseResponseMapper extends AbstractResponseMapper<HelpResponse, HelpResponseResponse> {

    private final HelpPostRepository helpPostRepository;
    private final UserRepository userRepository;

    public HelpResponseResponseMapper(HelpPostRepository helpPostRepository, UserRepository userRepository) {
        this.helpPostRepository = helpPostRepository;
        this.userRepository = userRepository;
    }

    @Override
    public List<HelpResponseResponse> toResponseList(List<HelpResponse> helpResponses) {
        if (helpResponses == null || helpResponses.isEmpty()) {
            return new ArrayList<>();
        }

        Set<Long> responderIds = helpResponses.stream().map(HelpResponse::getResponderId).collect(Collectors.toSet());
        Set<Long> postIds = helpResponses.stream().map(HelpResponse::getHelpPostId).collect(Collectors.toSet());

        Map<Long, String> responderNameMap = new HashMap<>();
        if (!responderIds.isEmpty()) {
            userRepository.findAllById(responderIds).forEach(u -> responderNameMap.put(u.getId(), u.getUsername()));
        }
        Map<Long, String> postTitleMap = new HashMap<>();
        if (!postIds.isEmpty()) {
            helpPostRepository.findAllById(postIds).forEach(p -> postTitleMap.put(p.getId(), p.getTitle()));
        }

        List<HelpResponseResponse> responses = new ArrayList<>();
        for (HelpResponse helpResponse : helpResponses) {
            HelpResponseResponse response = new HelpResponseResponse();
            response.setId(helpResponse.getId());
            response.setHelpPostId(helpResponse.getHelpPostId());
            response.setResponderId(helpResponse.getResponderId());
            response.setMessage(helpResponse.getMessage());
            response.setContactInfo(helpResponse.getContactInfo());
            response.setAccepted(helpResponse.isAccepted());
            response.setCreatedAt(helpResponse.getCreatedAt());

            response.setResponderName(responderNameMap.get(helpResponse.getResponderId()));
            response.setHelpPostTitle(postTitleMap.get(helpResponse.getHelpPostId()));

            responses.add(response);
        }
        return responses;
    }
}
