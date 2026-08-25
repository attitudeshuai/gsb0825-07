package com.toolshare.mapper;

import com.toolshare.dto.maintenanceplan.MaintenancePlanResponse;
import com.toolshare.entity.MaintenancePlan;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * MaintenancePlan 实体 -> MaintenancePlanResponse 的统一转换。
 * 工具名按集合批量加载，距下次维护天数/是否逾期基于同一参考日期计算。
 */
@Component
public class MaintenancePlanResponseMapper {

    private final ReferenceDataLoader referenceDataLoader;

    public MaintenancePlanResponseMapper(ReferenceDataLoader referenceDataLoader) {
        this.referenceDataLoader = referenceDataLoader;
    }

    public MaintenancePlanResponse toResponse(MaintenancePlan plan, LocalDate today) {
        if (plan == null) {
            return null;
        }
        return toResponseList(List.of(plan), today).get(0);
    }

    public List<MaintenancePlanResponse> toResponseList(List<MaintenancePlan> plans, LocalDate referenceDate) {
        if (plans == null || plans.isEmpty()) {
            return new ArrayList<>();
        }

        LocalDate today = referenceDate != null ? referenceDate : LocalDate.now();

        List<MaintenancePlan> validPlans = plans.stream()
                .filter(plan -> plan != null)
                .collect(Collectors.toList());
        if (validPlans.isEmpty()) {
            return new ArrayList<>();
        }

        Map<Long, String> toolNameMap = referenceDataLoader.getToolNames(
                validPlans.stream().map(MaintenancePlan::getToolId).collect(Collectors.toList()));

        List<MaintenancePlanResponse> responses = new ArrayList<>();
        for (MaintenancePlan plan : validPlans) {
            long daysUntilDue = ChronoUnit.DAYS.between(today, plan.getNextMaintenanceDate());
            boolean isOverdue = daysUntilDue < 0;

            responses.add(new MaintenancePlanResponse(
                    plan.getId(),
                    plan.getToolId(),
                    toolNameMap.get(plan.getToolId()),
                    plan.getIntervalDays(),
                    plan.getLastMaintenanceDate(),
                    plan.getNextMaintenanceDate(),
                    plan.getDescription(),
                    plan.getIsActive(),
                    plan.getCreatedAt(),
                    plan.getUpdatedAt(),
                    daysUntilDue,
                    isOverdue
            ));
        }
        return responses;
    }
}
