package com.toolshare.mapper;

import java.util.Collections;
import java.util.List;

/**
 * 实体到响应 DTO 的公共转换基类。
 *
 * <p>子类只需实现批量转换 {@link #toResponseList(List)}，其中集中完成字段拷贝与关联数据的批量加载；
 * 单条转换 {@link #toResponse(Object)} 统一委托给批量实现，避免「单条」「批量」两套重复的映射逻辑，
 * 同时让单条查询复用批量加载，消除逐字段查询关联数据带来的 N+1。</p>
 *
 * @param <E> 实体类型
 * @param <R> 响应 DTO 类型
 */
public abstract class AbstractResponseMapper<E, R> {

    /**
     * 批量转换：字段拷贝与关联数据加载的唯一实现入口。
     */
    public abstract List<R> toResponseList(List<E> entities);

    /**
     * 单条转换：委托批量实现，保证与批量转换完全一致的字段与关联数据。
     */
    public R toResponse(E entity) {
        if (entity == null) {
            return null;
        }
        List<R> responses = toResponseList(Collections.singletonList(entity));
        return responses.isEmpty() ? null : responses.get(0);
    }
}
