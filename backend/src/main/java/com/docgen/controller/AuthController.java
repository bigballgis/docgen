package com.docgen.controller;

import com.docgen.dto.*;
import com.docgen.entity.User;
import com.docgen.middleware.TenantContext;
import com.docgen.middleware.UserDetailsImpl;
import com.docgen.service.AuthService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

/**
 * 认证控制器
 * 处理用户登录、注册、用户信息查询、密码修改等认证相关操作
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /**
     * 获取当前登录用户信息
     * 从 SecurityContext 中提取 UserDetailsImpl
     *
     * @return 当前登录用户的 UserDetailsImpl
     */
    private UserDetailsImpl getCurrentUser() {
        return (UserDetailsImpl) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }

    /**
     * 用户登录
     * 验证用户名和密码，返回 JWT Token
     * 此接口无需认证，已在 SecurityConfig 白名单中配置
     *
     * @param loginRequest 登录请求（用户名、密码）
     * @return 登录响应（JWT Token 和用户信息）
     */
    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest loginRequest) {
        LoginResponse response = authService.login(loginRequest);
        return Result.success(response);
    }

    /**
     * 用户注册
     * 仅管理员可以注册新用户
     *
     * @param registerRequest 注册请求（用户名、密码、角色）
     * @return 注册成功的用户信息
     */
    @PostMapping("/register")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<UserInfo> register(@Valid @RequestBody RegisterRequest registerRequest) {
        String tenantId = TenantContext.getTenantId();
        UserInfo userInfo = authService.register(registerRequest, tenantId);
        return Result.success(userInfo);
    }

    /**
     * 获取当前用户信息
     * 从 SecurityContext 中获取已认证用户的详细信息
     *
     * @return 当前用户的详细信息
     */
    @GetMapping("/profile")
    public Result<UserInfo> getProfile() {
        UserDetailsImpl currentUser = getCurrentUser();
        UserInfo userInfo = authService.getProfile(currentUser.getId());
        return Result.success(userInfo);
    }

    /**
     * 获取用户列表（分页）
     * 仅管理员可以访问
     *
     * @param page 页码，从 0 开始，默认为 0
     * @param size 每页大小，默认为 20
     * @return 分页用户列表
     */
    @GetMapping("/users")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Page<User>> getUserList(
            @RequestParam(defaultValue = "0") int page,
            @Max(100) @RequestParam(defaultValue = "20") int size) {
        String tenantId = TenantContext.getTenantId();
        Page<User> users = authService.getUserList(tenantId, page, size);
        return Result.success(users);
    }

    /**
     * 修改用户角色
     * 仅管理员可以修改其他用户的角色
     *
     * @param id   用户ID
     * @param role 新角色名称
     * @return 更新后的用户信息
     */
    @PutMapping("/users/{id}/role")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<UserInfo> updateUserRole(
            @PathVariable Long id,
            @RequestParam String role) {
        UserInfo userInfo = authService.updateUserRole(id, role);
        return Result.success(userInfo);
    }

    /**
     * 启用/禁用用户
     * 仅管理员可以启用或禁用用户账户
     *
     * @param id     用户ID
     * @param status 用户状态（enabled/disabled）
     * @return 操作结果
     */
    @PutMapping("/users/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> updateUserStatus(
            @PathVariable Long id,
            @RequestParam String status) {
        authService.updateUserStatus(id, status);
        return Result.success(null, "用户状态更新成功");
    }

    /**
     * 修改密码
     * 已认证用户可以修改自己的密码
     *
     * @param request 修改密码请求（原密码、新密码）
     * @return 操作结果
     */
    @PutMapping("/password")
    public Result<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        UserDetailsImpl currentUser = getCurrentUser();
        authService.changePassword(currentUser.getId(), request);
        return Result.success(null, "密码修改成功");
    }
}
