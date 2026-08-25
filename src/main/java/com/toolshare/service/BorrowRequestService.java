package com.toolshare.service;

import com.toolshare.dto.PageResponse;
import com.toolshare.dto.borrowrequest.BorrowRequestResponse;
import com.toolshare.dto.borrowrequest.CreateBorrowRequestRequest;
import com.toolshare.dto.borrowrequest.UpdateBorrowRequestRequest;
import com.toolshare.dto.borrowrequest.UpdateBorrowStatusRequest;
import com.toolshare.entity.BorrowRequest;
import com.toolshare.entity.BorrowRequestStatus;
import com.toolshare.entity.NotificationType;
import com.toolshare.entity.Tool;
import com.toolshare.entity.ToolBox;
import com.toolshare.entity.ToolLogAction;
import com.toolshare.entity.ToolStatus;
import com.toolshare.entity.User;
import com.toolshare.exception.BadRequestException;
import com.toolshare.exception.ResourceNotFoundException;
import com.toolshare.repository.BorrowRequestRepository;
import com.toolshare.repository.OverdueRecordRepository;
import com.toolshare.repository.ToolBoxRepository;
import com.toolshare.repository.ToolRepository;
import com.toolshare.repository.UserRepository;
import com.toolshare.service.mapper.BorrowRequestResponseMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;

@Service
public class BorrowRequestService {

    private final BorrowRequestRepository borrowRequestRepository;
    private final ToolRepository toolRepository;
    private final ToolBoxRepository toolBoxRepository;
    private final UserRepository userRepository;
    private final ToolLogService toolLogService;
    private final NotificationService notificationService;
    private final OverdueRecordRepository overdueRecordRepository;
    private final BorrowRequestResponseMapper borrowRequestResponseMapper;

    public BorrowRequestService(BorrowRequestRepository borrowRequestRepository,
                                ToolRepository toolRepository,
                                ToolBoxRepository toolBoxRepository,
                                UserRepository userRepository,
                                ToolLogService toolLogService,
                                NotificationService notificationService,
                                OverdueRecordRepository overdueRecordRepository,
                                BorrowRequestResponseMapper borrowRequestResponseMapper) {
        this.borrowRequestRepository = borrowRequestRepository;
        this.toolRepository = toolRepository;
        this.toolBoxRepository = toolBoxRepository;
        this.userRepository = userRepository;
        this.toolLogService = toolLogService;
        this.notificationService = notificationService;
        this.overdueRecordRepository = overdueRecordRepository;
        this.borrowRequestResponseMapper = borrowRequestResponseMapper;
    }

    public PageResponse<BorrowRequestResponse> getAllBorrowRequests(BorrowRequestStatus status, Long requesterId,
                                                                    Long toolId, LocalDate startDate, LocalDate endDate,
                                                                    int page, int size, String sortBy, String sortDir) {
        Sort sort = sortDir.equalsIgnoreCase("desc") ? Sort.by(sortBy).descending() : Sort.by(sortBy).ascending();
        Pageable pageable = PageRequest.of(page, size, sort);

        Page<BorrowRequest> requestPage = borrowRequestRepository.search(status, requesterId, toolId, startDate, endDate, pageable);
        List<BorrowRequestResponse> responseList = borrowRequestResponseMapper.toResponseList(requestPage.getContent());
        return PageResponse.of(responseList, requestPage.getTotalElements(), requestPage.getNumber(), requestPage.getSize());
    }

    public BorrowRequestResponse getBorrowRequestById(Long id) {
        BorrowRequest borrowRequest = borrowRequestRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("借用申请不存在"));
        return borrowRequestResponseMapper.toResponse(borrowRequest);
    }

    public PageResponse<BorrowRequestResponse> getMyBorrowRequests(Long requesterId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<BorrowRequest> requestPage = borrowRequestRepository.findByRequesterId(requesterId, pageable);
        List<BorrowRequestResponse> responseList = borrowRequestResponseMapper.toResponseList(requestPage.getContent());
        return PageResponse.of(responseList, requestPage.getTotalElements(), requestPage.getNumber(), requestPage.getSize());
    }

    @Transactional
    public BorrowRequestResponse createBorrowRequest(CreateBorrowRequestRequest request, Long requesterId) {
        Tool tool = toolRepository.findById(request.getToolId())
                .orElseThrow(() -> new ResourceNotFoundException("工具不存在"));

        if (tool.getStatus() != ToolStatus.AVAILABLE) {
            if (tool.getStatus() == ToolStatus.DISABLED) {
                throw new BadRequestException("该工具已被管理员禁用，不可借用");
            }
            throw new BadRequestException("该工具当前不可借用");
        }

        ToolBox toolBox = toolBoxRepository.findById(tool.getBoxId())
                .orElseThrow(() -> new ResourceNotFoundException("工具箱不存在"));

        if (!Boolean.TRUE.equals(toolBox.getIsActive())) {
            throw new BadRequestException("该工具所属工具箱已停用，不可借用");
        }

        if (request.getStartDate().isAfter(request.getExpectedReturnDate())) {
            throw new BadRequestException("开始日期不能晚于预计归还日期");
        }

        if (tool.getMaxBorrowDays() != null && tool.getMaxBorrowDays() > 0) {
            long borrowDays = ChronoUnit.DAYS.between(request.getStartDate(), request.getExpectedReturnDate()) + 1;
            if (borrowDays > tool.getMaxBorrowDays()) {
                throw new BadRequestException(String.format(
                        "该工具单次借用最长允许 %d 天，当前选择的借用时长为 %d 天，请缩短借用时间",
                        tool.getMaxBorrowDays(), borrowDays
                ));
            }
        }

        checkBookingConflict(request.getToolId(), request.getStartDate(), request.getExpectedReturnDate(), null,
                Arrays.asList(BorrowRequestStatus.PENDING, BorrowRequestStatus.APPROVED));

        BorrowRequest borrowRequest = new BorrowRequest();
        borrowRequest.setToolId(request.getToolId());
        borrowRequest.setRequesterId(requesterId);
        borrowRequest.setStartDate(request.getStartDate());
        borrowRequest.setExpectedReturnDate(request.getExpectedReturnDate());
        borrowRequest.setRemark(request.getRemark());
        borrowRequest.setStatus(BorrowRequestStatus.PENDING);

        BorrowRequest savedRequest = borrowRequestRepository.save(borrowRequest);

        User requester = userRepository.findById(requesterId).orElse(null);
        String requesterName = requester != null ? requester.getUsername() : "未知用户";
        notificationService.createNotification(
                tool.getOwnerId(),
                NotificationType.NEW_BORROW_REQUEST,
                "新的借用申请",
                requesterName + " 申请借用您的工具「" + tool.getName() + "」，请及时处理。",
                savedRequest.getId()
        );

        return borrowRequestResponseMapper.toResponse(savedRequest);
    }

    @Transactional
    public BorrowRequestResponse updateBorrowRequest(Long id, UpdateBorrowRequestRequest request, Long currentUserId) {
        BorrowRequest borrowRequest = borrowRequestRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("借用申请不存在"));

        if (!borrowRequest.getRequesterId().equals(currentUserId)) {
            throw new BadRequestException("无权修改此借用申请");
        }

        if (borrowRequest.getStatus() != BorrowRequestStatus.PENDING) {
            throw new BadRequestException("只能修改待审核的申请");
        }

        LocalDate originalStartDate = borrowRequest.getStartDate();
        LocalDate originalExpectedReturnDate = borrowRequest.getExpectedReturnDate();
        boolean dateChanged = false;

        if (request.getStartDate() != null) {
            borrowRequest.setStartDate(request.getStartDate());
            dateChanged = true;
        }
        if (request.getExpectedReturnDate() != null) {
            borrowRequest.setExpectedReturnDate(request.getExpectedReturnDate());
            dateChanged = true;
        }
        if (request.getRemark() != null) {
            borrowRequest.setRemark(request.getRemark());
        }

        if (borrowRequest.getStartDate().isAfter(borrowRequest.getExpectedReturnDate())) {
            throw new BadRequestException("开始日期不能晚于预计归还日期");
        }

        Tool tool = toolRepository.findById(borrowRequest.getToolId())
                .orElseThrow(() -> new ResourceNotFoundException("工具不存在"));
        if (tool.getMaxBorrowDays() != null && tool.getMaxBorrowDays() > 0) {
            long borrowDays = ChronoUnit.DAYS.between(borrowRequest.getStartDate(), borrowRequest.getExpectedReturnDate()) + 1;
            if (borrowDays > tool.getMaxBorrowDays()) {
                throw new BadRequestException(String.format(
                        "该工具单次借用最长允许 %d 天，当前选择的借用时长为 %d 天，请缩短借用时间",
                        tool.getMaxBorrowDays(), borrowDays
                ));
            }
        }

        if (dateChanged) {
            checkBookingConflict(borrowRequest.getToolId(), borrowRequest.getStartDate(),
                    borrowRequest.getExpectedReturnDate(), borrowRequest.getId(),
                    Arrays.asList(BorrowRequestStatus.PENDING, BorrowRequestStatus.APPROVED));
        }

        BorrowRequest savedRequest = borrowRequestRepository.save(borrowRequest);
        return borrowRequestResponseMapper.toResponse(savedRequest);
    }

    @Transactional
    public BorrowRequestResponse updateBorrowStatus(Long id, UpdateBorrowStatusRequest request, Long currentUserId) {
        BorrowRequest borrowRequest = borrowRequestRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("借用申请不存在"));

        Tool tool = toolRepository.findById(borrowRequest.getToolId())
                .orElseThrow(() -> new ResourceNotFoundException("工具不存在"));

        boolean isOwner = tool.getOwnerId().equals(currentUserId);
        boolean isRequester = borrowRequest.getRequesterId().equals(currentUserId);

        if (!isOwner && !isRequester) {
            throw new BadRequestException("无权修改此申请状态");
        }

        BorrowRequestStatus newStatus = request.getStatus();

        switch (newStatus) {
            case APPROVED:
                if (!isOwner) {
                    throw new BadRequestException("只有工具所有者可以批准申请");
                }
                if (borrowRequest.getStatus() != BorrowRequestStatus.PENDING) {
                    throw new BadRequestException("只能批准待审核的申请");
                }
                if (tool.getStatus() != ToolStatus.AVAILABLE) {
                    throw new BadRequestException("工具当前不可借用");
                }
                checkBookingConflict(borrowRequest.getToolId(), borrowRequest.getStartDate(),
                        borrowRequest.getExpectedReturnDate(), borrowRequest.getId(),
                        Arrays.asList(BorrowRequestStatus.APPROVED));
                tool.setStatus(ToolStatus.BORROWED);
                toolRepository.save(tool);
                toolLogService.createLogInternal(tool.getId(), borrowRequest.getRequesterId(),
                        ToolLogAction.BORROW, "借用申请已批准");
                notificationService.createNotification(
                        borrowRequest.getRequesterId(),
                        NotificationType.BORROW_APPROVED,
                        "借用申请已批准",
                        "您申请借用的工具「" + tool.getName() + "」已被批准，请及时取用。",
                        borrowRequest.getId()
                );
                break;

            case REJECTED:
                if (!isOwner) {
                    throw new BadRequestException("只有工具所有者可以拒绝申请");
                }
                if (borrowRequest.getStatus() != BorrowRequestStatus.PENDING) {
                    throw new BadRequestException("只能拒绝待审核的申请");
                }
                notificationService.createNotification(
                        borrowRequest.getRequesterId(),
                        NotificationType.BORROW_REJECTED,
                        "借用申请被拒绝",
                        "您申请借用的工具「" + tool.getName() + "」被拒绝了。",
                        borrowRequest.getId()
                );
                break;

            case RETURNED:
                if (!isOwner) {
                    throw new BadRequestException("只有工具所有者可以确认归还");
                }
                if (borrowRequest.getStatus() != BorrowRequestStatus.APPROVED) {
                    throw new BadRequestException("只能归还已批准的申请");
                }
                boolean toolBoxActive = toolBoxRepository.findById(tool.getBoxId())
                        .map(ToolBox::getIsActive)
                        .map(active -> Boolean.TRUE.equals(active))
                        .orElse(false);
                if (toolBoxActive) {
                    tool.setStatus(ToolStatus.AVAILABLE);
                    tool.setStatusBeforeBoxDeactivated(null);
                } else {
                    tool.setStatus(ToolStatus.MAINTENANCE);
                    tool.setStatusBeforeBoxDeactivated(ToolStatus.AVAILABLE);
                }
                toolRepository.save(tool);
                borrowRequest.setActualReturnDate(LocalDate.now());
                toolLogService.createLogInternal(tool.getId(), borrowRequest.getRequesterId(),
                        ToolLogAction.RETURN, "工具已归还");
                notificationService.createNotification(
                        borrowRequest.getRequesterId(),
                        NotificationType.BORROW_RETURNED,
                        "工具归还已确认",
                        "您借用的工具「" + tool.getName() + "」归还已确认，感谢使用！",
                        borrowRequest.getId()
                );
                overdueRecordRepository.findByBorrowRequestId(borrowRequest.getId()).ifPresent(record -> {
                    if (!record.isResolved()) {
                        record.setResolved(true);
                        record.setResolvedAt(LocalDateTime.now());
                        overdueRecordRepository.save(record);
                    }
                });
                break;

            default:
                throw new BadRequestException("无效的状态变更");
        }

        borrowRequest.setStatus(newStatus);
        BorrowRequest savedRequest = borrowRequestRepository.save(borrowRequest);
        return borrowRequestResponseMapper.toResponse(savedRequest);
    }

    @Transactional
    public void deleteBorrowRequest(Long id, Long currentUserId) {
        BorrowRequest borrowRequest = borrowRequestRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("借用申请不存在"));

        if (!borrowRequest.getRequesterId().equals(currentUserId)) {
            throw new BadRequestException("无权删除此借用申请");
        }

        if (borrowRequest.getStatus() != BorrowRequestStatus.PENDING) {
            throw new BadRequestException("只能删除待审核的申请");
        }

        borrowRequestRepository.delete(borrowRequest);
    }

    public void checkBookingConflict(Long toolId, LocalDate startDate, LocalDate endDate, Long excludeId,
                                      List<BorrowRequestStatus> statuses) {
        List<BorrowRequest> conflicts = borrowRequestRepository.findConflictingBorrows(
                toolId, statuses, startDate, endDate, excludeId);

        if (!conflicts.isEmpty()) {
            BorrowRequest firstConflict = conflicts.get(0);
            userRepository.findById(firstConflict.getRequesterId()).ifPresentOrElse(
                    user -> {
                        throw new BadRequestException(String.format(
                                "该工具在 %s 至 %s 时段已被 %s 预约（状态：%s），请选择其他时段",
                                firstConflict.getStartDate(),
                                firstConflict.getExpectedReturnDate(),
                                user.getUsername(),
                                firstConflict.getStatus() == BorrowRequestStatus.PENDING ? "待审核" : "已批准"
                        ));
                    },
                    () -> {
                        throw new BadRequestException(String.format(
                                "该工具在 %s 至 %s 时段已被其他用户预约（状态：%s），请选择其他时段",
                                firstConflict.getStartDate(),
                                firstConflict.getExpectedReturnDate(),
                                firstConflict.getStatus() == BorrowRequestStatus.PENDING ? "待审核" : "已批准"
                        ));
                    }
            );
        }
    }
}
