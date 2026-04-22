package com.docgen.service;

import com.docgen.dto.CreateTenantRequest;
import com.docgen.dto.UpdateTenantRequest;
import com.docgen.entity.Tenant;
import com.docgen.exception.BusinessException;
import com.docgen.middleware.TenantContext;
import com.docgen.repository.TenantRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 租户服务
 * 提供租户的初始化、查询、创建和更新功能
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TenantService {

    private final TenantRepository tenantRepository;

    /**
     * 初始化默认租户
     * 应用启动时自动调用，如果不存在默认租户则创建
     */
    @PostConstruct
    public void initDefaultTenant() {
        try {
            if (tenantRepository.findByCode("default").isEmpty()) {
                Tenant tenant = Tenant.builder()
                        .name("默认租户")
                        .code("default")
                        .status("active")
                        .createdAt(LocalDateTime.now())
                        .updatedAt(LocalDateTime.now())
                        .build();
                tenantRepository.save(tenant);
                log.info("默认租户创建成功");
            } else {
                log.info("默认租户已存在，跳过创建");
            }
        } catch (Exception e) {
            log.warn("初始化默认租户失败（可能是数据库尚未就绪）: {}", e.getMessage());
        }
    }

    /**
     * 分页获取租户列表
     *
     * @param page 页码（从0开始）
     * @param size 每页大小
     * @return 租户分页列表
     */
    public Page<Tenant> getTenantList(int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return tenantRepository.findAll(pageable);
    }

    /**
     * 创建新租户
     *
     * @param request 创建租户请求
     * @return 新创建的租户实体
     */
    @Transactional
    public Tenant createTenant(CreateTenantRequest request) {
        String name = request.getName();
        // CreateTenantRequest 没有 code 字段，使用 name 作为 code
        String code = name;

        if (name == null || name.isBlank()) {
            throw new BusinessException("租户名称不能为空");
        }

        // 检查名称唯一性
        if (tenantRepository.existsByName(name)) {
            throw new BusinessException("租户名称已存在: " + name);
        }
        // 检查编码唯一性
        if (tenantRepository.existsByCode(code)) {
            throw new BusinessException("租户编码已存在: " + code);
        }

        Tenant tenant = Tenant.builder()
                .name(name)
                .code(code)
                .status(request.getStatus() != null ? request.getStatus() : "active")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        tenant = tenantRepository.save(tenant);
        log.info("租户创建成功: {} (编码: {})", name, code);
        return tenant;
    }

    /**
     * 更新租户信息
     *
     * @param id      租户ID
     * @param request 更新的租户数据
     * @return 更新后的租户实体
     */
    @Transactional
    public Tenant updateTenant(Long id, UpdateTenantRequest request) {
        Tenant tenant = tenantRepository.findById(id)
                .orElseThrow(() -> new BusinessException("租户不存在"));

        String name = request != null ? request.getName() : null;
        String status = request != null ? request.getStatus() : null;

        // 检查名称唯一性（如果提供了新名称）
        if (name != null && !name.isBlank() && !name.equals(tenant.getName())) {
            if (tenantRepository.existsByName(name)) {
                throw new BusinessException("租户名称已存在: " + name);
            }
            tenant.setName(name);
        }

        // 更新状态
        if (status != null && !status.isBlank()) {
            tenant.setStatus(status);
        }

        tenant.setUpdatedAt(LocalDateTime.now());
        tenant = tenantRepository.save(tenant);
        log.info("租户更新成功: {} (ID: {})", tenant.getName(), id);
        return tenant;
    }

    /**
     * 获取当前租户信息（无参版本，从 TenantContext 获取）
     *
     * @return 租户实体
     */
    public Tenant getCurrentTenant() {
        String tenantId = TenantContext.getTenantId();
        if (tenantId == null || tenantId.isEmpty()) {
            throw new BusinessException("未获取到当前租户信息");
        }

        // tenantId 可能是 ID（数字）或 code（字符串）
        try {
            Long id = Long.parseLong(tenantId);
            return tenantRepository.findById(id)
                    .orElseThrow(() -> new BusinessException("租户不存在"));
        } catch (NumberFormatException e) {
            // 不是数字，按 code 查询
            return tenantRepository.findByCode(tenantId)
                    .orElseThrow(() -> new BusinessException("租户不存在"));
        }
    }

    /**
     * 根据租户ID获取租户信息
     *
     * @param tenantId 租户ID
     * @return 租户实体
     */
    public Tenant getCurrentTenant(String tenantId) {
        // tenantId 可能是 ID（数字）或 code（字符串）
        try {
            Long id = Long.parseLong(tenantId);
            return tenantRepository.findById(id)
                    .orElseThrow(() -> new BusinessException("租户不存在"));
        } catch (NumberFormatException e) {
            // 不是数字，按 code 查询
            return tenantRepository.findByCode(tenantId)
                    .orElseThrow(() -> new BusinessException("租户不存在"));
        }
    }

    /**
     * 根据租户编码获取租户
     *
     * @param code 租户编码
     * @return 租户实体
     */
    public Tenant getTenantByCode(String code) {
        return tenantRepository.findByCode(code)
                .orElseThrow(() -> new BusinessException("租户不存在: " + code));
    }
}
