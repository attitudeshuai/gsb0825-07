package com.toolshare.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Page;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PageResponse<T> {
    private List<T> content;
    private int pageNumber;
    private int pageSize;
    private long totalElements;
    private int totalPages;
    private boolean first;
    private boolean last;

    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isFirst(),
                page.isLast()
        );
    }

    /**
     * 使用源分页对象的元数据，配合已整页转换好的内容列表构造响应。
     *
     * <p>用于「先对整页 {@code getContent()} 一次性批量转换，再组装分页响应」的场景，
     * 避免 {@code page.map(mapper::toResponse)} 逐行触发单元素批量查询造成的 N+1。
     * 分页元数据（页码、每页大小、总数、首末页等）与 {@link #from(Page)} 完全一致。</p>
     */
    public static <T> PageResponse<T> from(Page<?> page, List<T> content) {
        return new PageResponse<>(
                content,
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isFirst(),
                page.isLast()
        );
    }

    public static <T> PageResponse<T> of(List<T> content, long totalElements, int pageNumber, int pageSize) {
        int totalPages = pageSize > 0 ? (int) Math.ceil((double) totalElements / pageSize) : 0;
        return new PageResponse<>(
                content,
                pageNumber,
                pageSize,
                totalElements,
                totalPages,
                pageNumber == 0,
                pageNumber >= totalPages - 1
        );
    }
}
