package com.toolshare.service.mapper;

import com.toolshare.dto.PageResponse;
import com.toolshare.dto.maintenanceplan.MaintenancePlanResponse;
import com.toolshare.entity.MaintenancePlan;
import com.toolshare.repository.ToolRepository;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * MaintenancePlan -> MaintenancePlanResponse 的统一转换组件。
 * 单条与批量共用同一套字段映射，工具名按 ID 批量加载，避免 N+1。
 */
@Component
public class MaintenancePlanResponseMapper {

    private final ToolRepository toolRepository;

    public MaintenancePlanResponseMapper(ToolRepository toolRepository) {
        this.toolRepository = toolRepository;
    }

    public MaintenancePlanResponse toResponse(MaintenancePlan plan) {
        return toResponseList(Collections.singletonList(plan)).get(0);
    }

    public List<MaintenancePlanResponse> toResponseList(List<MaintenancePlan> plans) {
        if (plans == null || plans.isEmpty()) {
            return new ArrayList<>();
        }

        plans = plans.stream().filter(Objects::nonNull).collect(Collectors.toList());

        List<Long> toolIds = plans.stream().map(MaintenancePlan::getToolId).collect(Collectors.toList());
        Map<Long, String> toolNameMap = new HashMap<>();
        toolRepository.findAllById(toolIds).forEach(t -> toolNameMap.put(t.getId(), t.getName()));

        LocalDate today = LocalDate.now();
        List<MaintenancePlanResponse> responses = new ArrayList<>();
        for (MaintenancePlan plan : plans) {
            responses.add(buildResponse(plan, toolNameMap.get(plan.getToolId()), today));
        }
        return responses;
    }

    public PageResponse<MaintenancePlanResponse> toPageResponse(Page<MaintenancePlan> page) {
        return PageResponse.of(toResponseList(page.getContent()),
                page.getTotalElements(), page.getNumber(), page.getSize());
    }

    private MaintenancePlanResponse buildResponse(MaintenancePlan plan, String toolName, LocalDate today) {
        long daysUntilDue = ChronoUnit.DAYS.between(today, plan.getNextMaintenanceDate());
        boolean isOverdue = daysUntilDue < 0;

        return new MaintenancePlanResponse(
                plan.getId(),
                plan.getToolId(),
                toolName,
                plan.getIntervalDays(),
                plan.getLastMaintenanceDate(),
                plan.getNextMaintenanceDate(),
                plan.getDescription(),
                plan.getIsActive(),
                plan.getCreatedAt(),
                plan.getUpdatedAt(),
                daysUntilDue,
                isOverdue
        );
    }
}
