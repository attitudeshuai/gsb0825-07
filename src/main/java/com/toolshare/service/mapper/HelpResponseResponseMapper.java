package com.toolshare.service.mapper;

import com.toolshare.dto.PageResponse;
import com.toolshare.dto.helppost.HelpResponseResponse;
import com.toolshare.entity.HelpResponse;
import com.toolshare.repository.HelpPostRepository;
import com.toolshare.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * HelpResponse -> HelpResponseResponse 的统一转换组件。
 * 单条与批量共用同一套字段映射，响应者姓名、求助帖标题按 ID 批量加载，避免 N+1。
 */
@Component
public class HelpResponseResponseMapper {

    private final HelpPostRepository helpPostRepository;
    private final UserRepository userRepository;

    public HelpResponseResponseMapper(HelpPostRepository helpPostRepository, UserRepository userRepository) {
        this.helpPostRepository = helpPostRepository;
        this.userRepository = userRepository;
    }

    public HelpResponseResponse toResponse(HelpResponse helpResponse) {
        return toResponseList(Collections.singletonList(helpResponse)).get(0);
    }

    public List<HelpResponseResponse> toResponseList(List<HelpResponse> helpResponses) {
        if (helpResponses == null || helpResponses.isEmpty()) {
            return new ArrayList<>();
        }

        Set<Long> responderIds = helpResponses.stream().map(HelpResponse::getResponderId).collect(Collectors.toSet());
        Set<Long> helpPostIds = helpResponses.stream().map(HelpResponse::getHelpPostId).collect(Collectors.toSet());

        Map<Long, String> responderNameMap = new HashMap<>();
        if (!responderIds.isEmpty()) {
            userRepository.findAllById(responderIds).forEach(u -> responderNameMap.put(u.getId(), u.getUsername()));
        }
        Map<Long, String> helpPostTitleMap = new HashMap<>();
        if (!helpPostIds.isEmpty()) {
            helpPostRepository.findAllById(helpPostIds).forEach(p -> helpPostTitleMap.put(p.getId(), p.getTitle()));
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
            response.setHelpPostTitle(helpPostTitleMap.get(helpResponse.getHelpPostId()));
            responses.add(response);
        }
        return responses;
    }

    public PageResponse<HelpResponseResponse> toPageResponse(Page<HelpResponse> page) {
        return PageResponse.of(toResponseList(page.getContent()),
                page.getTotalElements(), page.getNumber(), page.getSize());
    }
}
