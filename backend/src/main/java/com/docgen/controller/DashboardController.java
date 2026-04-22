package com.docgen.controller;

import com.docgen.dto.Result;
import com.docgen.middleware.TenantContext;
import com.docgen.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 仪表盘控制器
 * 提供系统统计数据查询接口
 */
@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    /**
     * 获取仪表盘统计数据
     * 包含模板总数、文档总数、用户总数、待审批数等统计信息
     *
     * @return 统计数据
     */
    @GetMapping("/stats")
    public Result<Map<String, Object>> getDashboardStats() {
        String tenantId = TenantContext.getTenantId();
        Map<String, Object> stats = dashboardService.getDashboardStats(tenantId);
        return Result.success(stats);
    }
}
