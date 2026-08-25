package com.toolshare.service;

import com.toolshare.dto.PageResponse;
import com.toolshare.dto.helppost.*;
import com.toolshare.entity.*;
import com.toolshare.exception.BadRequestException;
import com.toolshare.exception.ResourceNotFoundException;
import com.toolshare.mapper.HelpPostResponseMapper;
import com.toolshare.mapper.HelpResponseResponseMapper;
import com.toolshare.repository.HelpPostRepository;
import com.toolshare.repository.HelpResponseRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class HelpBoardService {

    private final HelpPostRepository helpPostRepository;
    private final HelpResponseRepository helpResponseRepository;
    private final HelpPostResponseMapper helpPostResponseMapper;
    private final HelpResponseResponseMapper helpResponseResponseMapper;

    public HelpBoardService(HelpPostRepository helpPostRepository,
                            HelpResponseRepository helpResponseRepository,
                            HelpPostResponseMapper helpPostResponseMapper,
                            HelpResponseResponseMapper helpResponseResponseMapper) {
        this.helpPostRepository = helpPostRepository;
        this.helpResponseRepository = helpResponseRepository;
        this.helpPostResponseMapper = helpPostResponseMapper;
        this.helpResponseResponseMapper = helpResponseResponseMapper;
    }

    @Transactional
    public HelpPostResponse createHelpPost(CreateHelpPostRequest request, Long posterId) {
        HelpPost helpPost = new HelpPost();
        helpPost.setPosterId(posterId);
        helpPost.setTitle(request.getTitle());
        helpPost.setContent(request.getContent());
        helpPost.setCategory(request.getCategory());
        helpPost.setLocation(request.getLocation());
        helpPost.setDeadline(request.getDeadline());
        helpPost.setStatus(HelpPostStatus.OPEN);

        HelpPost savedPost = helpPostRepository.save(helpPost);
        return helpPostResponseMapper.toResponse(savedPost);
    }

    @Transactional
    public HelpPostResponse updateHelpPost(Long postId, UpdateHelpPostRequest request, Long userId) {
        HelpPost helpPost = helpPostRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("求助帖不存在"));

        if (!helpPost.getPosterId().equals(userId)) {
            throw new BadRequestException("只有发布者可以修改此求助帖");
        }

        if (request.getTitle() != null) {
            helpPost.setTitle(request.getTitle());
        }
        if (request.getContent() != null) {
            helpPost.setContent(request.getContent());
        }
        if (request.getCategory() != null) {
            helpPost.setCategory(request.getCategory());
        }
        if (request.getLocation() != null) {
            helpPost.setLocation(request.getLocation());
        }
        if (request.getDeadline() != null) {
            helpPost.setDeadline(request.getDeadline());
        }
        if (request.getStatus() != null) {
            helpPost.setStatus(request.getStatus());
        }

        HelpPost savedPost = helpPostRepository.save(helpPost);
        return helpPostResponseMapper.toResponse(savedPost);
    }

    @Transactional
    public void deleteHelpPost(Long postId, Long userId) {
        HelpPost helpPost = helpPostRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("求助帖不存在"));

        if (!helpPost.getPosterId().equals(userId)) {
            throw new BadRequestException("只有发布者可以删除此求助帖");
        }

        helpPostRepository.delete(helpPost);
    }

    public HelpPostResponse getHelpPostById(Long postId) {
        HelpPost helpPost = helpPostRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("求助帖不存在"));
        return helpPostResponseMapper.toResponse(helpPost);
    }

    public PageResponse<HelpPostResponse> getAllHelpPosts(int page, int size, String category, List<HelpPostStatus> statuses) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<HelpPost> postPage;

        if (statuses != null && !statuses.isEmpty() && category != null && !category.isEmpty()) {
            postPage = helpPostRepository.findByStatusInAndCategory(statuses, category, pageable);
        } else if (statuses != null && !statuses.isEmpty()) {
            postPage = helpPostRepository.findByStatusIn(statuses, pageable);
        } else if (category != null && !category.isEmpty()) {
            postPage = helpPostRepository.findByCategory(category, pageable);
        } else {
            postPage = helpPostRepository.findAll(pageable);
        }

        List<HelpPostResponse> responseList = helpPostResponseMapper.toResponseList(postPage.getContent());
        return PageResponse.of(responseList, postPage.getTotalElements(), postPage.getNumber(), postPage.getSize());
    }

    public PageResponse<HelpPostResponse> getMyHelpPosts(Long posterId, int page, int size, HelpPostStatus status) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<HelpPost> postPage;

        if (status != null) {
            postPage = helpPostRepository.findByPosterIdAndStatus(posterId, status, pageable);
        } else {
            postPage = helpPostRepository.findByPosterId(posterId, pageable);
        }

        List<HelpPostResponse> responseList = helpPostResponseMapper.toResponseList(postPage.getContent());
        return PageResponse.of(responseList, postPage.getTotalElements(), postPage.getNumber(), postPage.getSize());
    }

    public PageResponse<HelpPostResponse> getAcceptedHelpPosts(Long acceptedResponderId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<HelpPost> postPage = helpPostRepository.findByAcceptedResponderId(acceptedResponderId, pageable);
        List<HelpPostResponse> responseList = helpPostResponseMapper.toResponseList(postPage.getContent());
        return PageResponse.of(responseList, postPage.getTotalElements(), postPage.getNumber(), postPage.getSize());
    }

    @Transactional
    public HelpResponseResponse createHelpResponse(Long postId, CreateHelpResponseRequest request, Long responderId) {
        HelpPost helpPost = helpPostRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("求助帖不存在"));

        if (helpPost.getStatus() != HelpPostStatus.OPEN) {
            throw new BadRequestException("该求助帖当前状态不接受响应");
        }

        if (helpPost.getPosterId().equals(responderId)) {
            throw new BadRequestException("不能响应自己发布的求助帖");
        }

        if (helpResponseRepository.existsByHelpPostIdAndResponderId(postId, responderId)) {
            throw new BadRequestException("您已经响应过此求助帖");
        }

        HelpResponse helpResponse = new HelpResponse();
        helpResponse.setHelpPostId(postId);
        helpResponse.setResponderId(responderId);
        helpResponse.setMessage(request.getMessage());
        helpResponse.setContactInfo(request.getContactInfo());
        helpResponse.setAccepted(false);

        HelpResponse savedResponse = helpResponseRepository.save(helpResponse);
        return helpResponseResponseMapper.toResponse(savedResponse);
    }

    @Transactional
    public HelpResponseResponse acceptHelpResponse(Long postId, Long responseId, Long userId) {
        HelpPost helpPost = helpPostRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("求助帖不存在"));

        if (!helpPost.getPosterId().equals(userId)) {
            throw new BadRequestException("只有发布者可以接受响应");
        }

        if (helpPost.getStatus() != HelpPostStatus.OPEN) {
            throw new BadRequestException("该求助帖当前状态不能接受响应");
        }

        HelpResponse helpResponse = helpResponseRepository.findById(responseId)
                .orElseThrow(() -> new ResourceNotFoundException("响应不存在"));

        if (!helpResponse.getHelpPostId().equals(postId)) {
            throw new BadRequestException("该响应不属于此求助帖");
        }

        helpResponse.setAccepted(true);
        helpResponseRepository.save(helpResponse);

        helpPost.setStatus(HelpPostStatus.ASSIGNED);
        helpPost.setAcceptedResponderId(helpResponse.getResponderId());
        helpPostRepository.save(helpPost);

        return helpResponseResponseMapper.toResponse(helpResponse);
    }

    @Transactional
    public HelpPostResponse completeHelpPost(Long postId, Long userId) {
        HelpPost helpPost = helpPostRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("求助帖不存在"));

        if (!helpPost.getPosterId().equals(userId) && !helpPost.getAcceptedResponderId().equals(userId)) {
            throw new BadRequestException("只有发布者或被接受的响应者可以标记完成");
        }

        if (helpPost.getStatus() != HelpPostStatus.ASSIGNED) {
            throw new BadRequestException("只有已分配状态的求助帖可以标记完成");
        }

        helpPost.setStatus(HelpPostStatus.COMPLETED);
        HelpPost savedPost = helpPostRepository.save(helpPost);
        return helpPostResponseMapper.toResponse(savedPost);
    }

    @Transactional
    public HelpPostResponse cancelHelpPost(Long postId, Long userId) {
        HelpPost helpPost = helpPostRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("求助帖不存在"));

        if (!helpPost.getPosterId().equals(userId)) {
            throw new BadRequestException("只有发布者可以取消求助帖");
        }

        if (helpPost.getStatus() == HelpPostStatus.COMPLETED) {
            throw new BadRequestException("已完成的求助帖不能取消");
        }

        helpPost.setStatus(HelpPostStatus.CANCELLED);
        HelpPost savedPost = helpPostRepository.save(helpPost);
        return helpPostResponseMapper.toResponse(savedPost);
    }

    public List<HelpResponseResponse> getHelpResponsesByPostId(Long postId) {
        HelpPost helpPost = helpPostRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("求助帖不存在"));

        List<HelpResponse> responses = helpResponseRepository.findByHelpPostIdOrderByCreatedAtDesc(postId);
        return helpResponseResponseMapper.toResponseList(responses);
    }

    public PageResponse<HelpResponseResponse> getMyHelpResponses(Long responderId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<HelpResponse> responsePage = helpResponseRepository.findByResponderId(responderId, pageable);
        List<HelpResponseResponse> responseList = helpResponseResponseMapper.toResponseList(responsePage.getContent());
        return PageResponse.of(responseList, responsePage.getTotalElements(), responsePage.getNumber(), responsePage.getSize());
    }
}
