package com.toolshare.mapper;

import com.toolshare.dto.maintenanceplan.MaintenancePlanResponse;
import com.toolshare.entity.MaintenancePlan;
import com.toolshare.entity.Tool;
import com.toolshare.repository.ToolRepository;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 维护计划实体到 {@link MaintenancePlanResponse} 的公共转换器。
 *
 * <p>工具名批量加载，到期天数与是否逾期基于当天计算，单条转换复用批量逻辑。</p>
 */
@Component
public class MaintenancePlanResponseMapper extends AbstractResponseMapper<MaintenancePlan, MaintenancePlanResponse> {

    private final ToolRepository toolRepository;

    public MaintenancePlanResponseMapper(ToolRepository toolRepository) {
        this.toolRepository = toolRepository;
    }

    @Override
    public List<MaintenancePlanResponse> toResponseList(List<MaintenancePlan> plans) {
        if (plans == null || plans.isEmpty()) {
            return new ArrayList<>();
        }

        List<MaintenancePlan> validPlans = plans.stream().filter(p -> p != null).collect(Collectors.toList());
        if (validPlans.isEmpty()) {
            return new ArrayList<>();
        }

        LocalDate today = LocalDate.now();

        List<Long> toolIds = validPlans.stream().map(MaintenancePlan::getToolId).collect(Collectors.toList());
        Map<Long, String> toolNameMap = new HashMap<>();
        toolRepository.findAllById(toolIds).forEach(t -> toolNameMap.put(t.getId(), t.getName()));

        List<MaintenancePlanResponse> responses = new ArrayList<>();
        for (MaintenancePlan plan : validPlans) {
            responses.add(buildResponse(plan, toolNameMap.get(plan.getToolId()), today));
        }
        return responses;
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
