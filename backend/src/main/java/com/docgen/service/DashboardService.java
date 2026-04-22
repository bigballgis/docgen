package com.docgen.service;

import com.docgen.repository.DashboardRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * 仪表盘统计服务
 * 提供系统概览统计数据
 */
@Service
@RequiredArgsConstructor
public class DashboardService {

    private final DashboardRepository dashboardRepository;

    /**
     * 获取仪表盘统计数据
     * @param tenantId 租户ID
     * @return 统计数据Map
     */
    @Cacheable(value = "dashboardStats", key = "#tenantId", unless = "#result == null")
    public Map<String, Object> getDashboardStats(String tenantId) {
        Map<String, Object> stats = new HashMap<>();

        stats.put("templateCount", dashboardRepository.countTemplates(tenantId));
        stats.put("publishedTemplateCount", dashboardRepository.countPublishedTemplates(tenantId));
        stats.put("pendingApprovalCount", dashboardRepository.countPendingTemplates(tenantId));
        stats.put("documentCount", dashboardRepository.countDocuments(tenantId));
        stats.put("userCount", dashboardRepository.countUsers(tenantId));
        stats.put("fragmentCount", dashboardRepository.countFragments(tenantId));

        return stats;
    }
}
