package com.toolshare.service;

import com.toolshare.dto.PageResponse;
import com.toolshare.dto.overduerecord.OverdueRecordResponse;
import com.toolshare.entity.BorrowRequest;
import com.toolshare.entity.OverdueRecord;
import com.toolshare.exception.BadRequestException;
import com.toolshare.exception.ResourceNotFoundException;
import com.toolshare.mapper.OverdueRecordResponseMapper;
import com.toolshare.repository.BorrowRequestRepository;
import com.toolshare.repository.OverdueRecordRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

@Service
public class OverdueRecordService {

    private final OverdueRecordRepository overdueRecordRepository;
    private final BorrowRequestRepository borrowRequestRepository;
    private final OverdueRecordResponseMapper overdueRecordResponseMapper;

    public OverdueRecordService(OverdueRecordRepository overdueRecordRepository,
                                BorrowRequestRepository borrowRequestRepository,
                                OverdueRecordResponseMapper overdueRecordResponseMapper) {
        this.overdueRecordRepository = overdueRecordRepository;
        this.borrowRequestRepository = borrowRequestRepository;
        this.overdueRecordResponseMapper = overdueRecordResponseMapper;
    }

    @Transactional
    public OverdueRecord createOrUpdateOverdueRecord(BorrowRequest borrowRequest) {
        if (borrowRequest.getActualReturnDate() != null) {
            throw new BadRequestException("该借用申请已归还，无需记录逾期");
        }

        LocalDate today = LocalDate.now();
        if (!borrowRequest.getExpectedReturnDate().isBefore(today)) {
            throw new BadRequestException("该借用申请尚未逾期");
        }

        Optional<OverdueRecord> existingOpt = overdueRecordRepository.findByBorrowRequestId(borrowRequest.getId());
        OverdueRecord record;

        if (existingOpt.isPresent()) {
            record = existingOpt.get();
            int overdueDays = (int) ChronoUnit.DAYS.between(borrowRequest.getExpectedReturnDate(), today);
            record.setOverdueDays(overdueDays);
        } else {
            record = new OverdueRecord();
            record.setBorrowRequestId(borrowRequest.getId());
            record.setToolId(borrowRequest.getToolId());
            record.setRequesterId(borrowRequest.getRequesterId());
            record.setExpectedReturnDate(borrowRequest.getExpectedReturnDate());
            record.setOverdueDate(borrowRequest.getExpectedReturnDate().plusDays(1));
            int overdueDays = (int) ChronoUnit.DAYS.between(borrowRequest.getExpectedReturnDate(), today);
            record.setOverdueDays(overdueDays);
            record.setResolved(false);
        }

        return overdueRecordRepository.save(record);
    }

    /**
     * 工具归还后将关联的逾期记录标记为已解决（若存在且未解决）。
     * 借用状态流转与扫码归还共用，避免逻辑重复。
     */
    @Transactional
    public void markResolvedIfExists(Long borrowRequestId) {
        overdueRecordRepository.findByBorrowRequestId(borrowRequestId).ifPresent(record -> {
            if (!record.isResolved()) {
                record.setResolved(true);
                record.setResolvedAt(LocalDateTime.now());
                overdueRecordRepository.save(record);
            }
        });
    }

    @Transactional
    public OverdueRecordResponse resolveOverdueRecord(Long id, Long currentUserId) {
        OverdueRecord record = overdueRecordRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("逾期记录不存在"));

        if (!record.getRequesterId().equals(currentUserId)) {
            throw new BadRequestException("无权处理此逾期记录");
        }

        if (record.isResolved()) {
            throw new BadRequestException("该逾期记录已处理");
        }

        BorrowRequest borrowRequest = borrowRequestRepository.findById(record.getBorrowRequestId())
                .orElseThrow(() -> new ResourceNotFoundException("借用申请不存在"));

        if (borrowRequest.getActualReturnDate() == null) {
            throw new BadRequestException("工具尚未归还，无法标记逾期记录为已处理");
        }

        record.setResolved(true);
        record.setResolvedAt(LocalDateTime.now());
        OverdueRecord saved = overdueRecordRepository.save(record);
        return overdueRecordResponseMapper.toResponse(saved);
    }

    public PageResponse<OverdueRecordResponse> getAllOverdueRecords(Boolean resolved, Long requesterId,
                                                                    int page, int size, String sortBy, String sortDir,
                                                                    Long currentUserId) {
        if (requesterId != null && !requesterId.equals(currentUserId)) {
            throw new BadRequestException("无权查看他人的逾期记录");
        }
        Long filteredRequesterId = currentUserId;

        Sort sort = sortDir.equalsIgnoreCase("desc") ? Sort.by(sortBy).descending() : Sort.by(sortBy).ascending();
        Pageable pageable = PageRequest.of(page, size, sort);

        Page<OverdueRecord> recordPage;
        if (resolved != null && filteredRequesterId != null) {
            recordPage = overdueRecordRepository.findByRequesterIdAndResolved(filteredRequesterId, resolved, pageable);
        } else if (resolved != null) {
            recordPage = overdueRecordRepository.findByResolved(resolved, pageable);
        } else if (filteredRequesterId != null) {
            recordPage = overdueRecordRepository.findByRequesterId(filteredRequesterId, pageable);
        } else {
            recordPage = overdueRecordRepository.findAll(pageable);
        }

        List<OverdueRecordResponse> responseList = overdueRecordResponseMapper.toResponseList(recordPage.getContent());
        return PageResponse.of(responseList, recordPage.getTotalElements(), recordPage.getNumber(), recordPage.getSize());
    }

    public OverdueRecordResponse getOverdueRecordById(Long id, Long currentUserId) {
        OverdueRecord record = overdueRecordRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("逾期记录不存在"));

        if (!record.getRequesterId().equals(currentUserId)) {
            throw new BadRequestException("无权查看此逾期记录");
        }

        return overdueRecordResponseMapper.toResponse(record);
    }

    public PageResponse<OverdueRecordResponse> getMyOverdueRecords(Long requesterId, Boolean resolved, int page, int size) {
        Sort sort = Sort.by("createdAt").descending();
        Pageable pageable = PageRequest.of(page, size, sort);

        Page<OverdueRecord> recordPage;
        if (resolved != null) {
            recordPage = overdueRecordRepository.findByRequesterIdAndResolved(requesterId, resolved, pageable);
        } else {
            recordPage = overdueRecordRepository.findByRequesterId(requesterId, pageable);
        }

        List<OverdueRecordResponse> responseList = overdueRecordResponseMapper.toResponseList(recordPage.getContent());
        return PageResponse.of(responseList, recordPage.getTotalElements(), recordPage.getNumber(), recordPage.getSize());
    }

    public long getUnresolvedCount() {
        return overdueRecordRepository.countByResolved(false);
    }

    public long getTotalOverdueCount() {
        return overdueRecordRepository.count();
    }

    public long getOverdueCountByDateRange(LocalDate startDate, LocalDate endDate) {
        return overdueRecordRepository.countByOverdueDateBetween(startDate, endDate);
    }
}
