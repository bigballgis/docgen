package com.docgen.controller;

import com.docgen.dto.CreateTenantRequest;
import com.docgen.dto.Result;
import com.docgen.dto.UpdateTenantRequest;
import com.docgen.entity.Tenant;
import com.docgen.service.TenantService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * 租户控制器
 * 处理租户的增删改查操作
 */
@RestController
@RequestMapping("/api/v1/tenants")
@RequiredArgsConstructor
public class TenantController {

    private final TenantService tenantService;

    /**
     * 获取租户列表（分页）
     * 仅管理员可以访问
     *
     * @param page 页码，从 0 开始，默认为 0
     * @param size 每页大小，默认为 20
     * @return 分页租户列表
     */
    @GetMapping("")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Page<Tenant>> getTenantList(
            @RequestParam(defaultValue = "0") int page,
            @Max(100) @RequestParam(defaultValue = "20") int size) {
        Page<Tenant> tenants = tenantService.getTenantList(page, size);
        return Result.success(tenants);
    }

    /**
     * 创建租户
     * 仅管理员可以创建新租户
     *
     * @param tenantData 租户数据（名称、编码、配置等）
     * @return 创建成功的租户信息
     */
    @PostMapping("")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Tenant> createTenant(@Valid @RequestBody CreateTenantRequest tenantData) {
        Tenant tenant = tenantService.createTenant(tenantData);
        return Result.success(tenant);
    }

    /**
     * 更新租户
     * 仅管理员可以更新租户信息
     *
     * @param id         租户ID
     * @param tenantData 更新的租户数据
     * @return 更新后的租户信息
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Tenant> updateTenant(
            @PathVariable Long id,
            @Valid @RequestBody UpdateTenantRequest tenantData) {
        Tenant tenant = tenantService.updateTenant(id, tenantData);
        return Result.success(tenant);
    }

    /**
     * 获取当前租户信息
     * 此接口无需认证，已在 SecurityConfig 白名单中配置
     *
     * @return 当前租户信息
     */
    @GetMapping("/current")
    public Result<Tenant> getCurrentTenant() {
        Tenant tenant = tenantService.getCurrentTenant();
        return Result.success(tenant);
    }
}
