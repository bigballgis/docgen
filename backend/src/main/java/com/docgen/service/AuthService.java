package com.docgen.service;

import com.docgen.dto.ChangePasswordRequest;
import com.docgen.dto.LoginRequest;
import com.docgen.dto.LoginResponse;
import com.docgen.dto.RegisterRequest;
import com.docgen.dto.UserInfo;
import com.docgen.entity.User;
import com.docgen.exception.BusinessException;
import com.docgen.middleware.JwtTokenProvider;
import com.docgen.repository.TenantRepository;
import com.docgen.repository.UserRepository;
import com.docgen.util.PasswordUtil;
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
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 认证服务
 * 提供用户注册、登录、密码管理等功能
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final TenantRepository tenantRepository;

    // 登录失败计数器 (生产环境应使用 Redis)
    private final ConcurrentHashMap<String, Integer> loginFailCount = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> lockedAccounts = new ConcurrentHashMap<>();
    private static final int MAX_LOGIN_ATTEMPTS = 5;
    private static final long LOCK_DURATION_MS = 15 * 60 * 1000; // 15分钟

    /** 允许的用户角色集合 */
    private static final Set<String> ALLOWED_ROLES = Set.of("admin", "user");

    /**
     * 初始化默认管理员账户
     * 应用启动时自动调用，如果不存在 admin 用户则创建
     */
    @PostConstruct
    public void initDefaultAdmin() {
        try {
            if (userRepository.findByUsername("admin").isEmpty()) {
                String defaultPassword = System.getenv("ADMIN_DEFAULT_PASSWORD");
                if (defaultPassword == null || defaultPassword.isBlank()) {
                    defaultPassword = "Admin@" + UUID.randomUUID().toString().substring(0, 8);
                    log.warn("未设置 ADMIN_DEFAULT_PASSWORD 环境变量，已生成随机密码: {}", defaultPassword);
                }

                User admin = User.builder()
                        .username("admin")
                        .passwordHash(PasswordUtil.hash(defaultPassword))
                        .role("admin")
                        .tenantId("default")
                        .createdAt(LocalDateTime.now())
                        .build();
                userRepository.save(admin);
                log.info("默认管理员账户创建成功");
            } else {
                log.info("默认管理员账户已存在，跳过创建");
            }
        } catch (Exception e) {
            log.warn("初始化默认管理员账户失败（可能是数据库尚未就绪）: {}", e.getMessage());
        }
    }

    /**
     * 注册新用户
     * 仅管理员可注册新用户
     *
     * @param request          注册请求信息
     * @param operatorTenantId 操作者的租户ID
     * @return 新创建的用户信息
     */
    @Transactional
    public UserInfo register(RegisterRequest request, String operatorTenantId) {
        String username = request.getUsername();
        String password = request.getPassword();

        // 检查用户名唯一性
        if (userRepository.findByUsername(username).isPresent()) {
            throw new BusinessException("用户名已存在: " + username);
        }

        // 检查用户名长度（3-32字符）
        if (username.length() < 3 || username.length() > 32) {
            throw new BusinessException("用户名长度必须在3到32个字符之间");
        }

        // 检查密码长度（最少6字符）
        if (password.length() < 6) {
            throw new BusinessException("密码长度不能少于6个字符");
        }

        // 确定角色
        String role = request.getRole();
        if (role == null || role.isBlank()) {
            role = "user";
        }
        if (!ALLOWED_ROLES.contains(role)) {
            throw new BusinessException("无效的用户角色，仅允许: admin, user");
        }

        // 使用 PasswordUtil 哈希密码并保存
        User user = User.builder()
                .username(username)
                .passwordHash(PasswordUtil.hash(password))
                .role(role)
                .tenantId(operatorTenantId != null ? operatorTenantId : "default")
                .createdAt(LocalDateTime.now())
                .build();

        user = userRepository.save(user);
        log.info("用户注册成功: {} (角色: {})", username, role);

        return convertToUserInfo(user);
    }

    /**
     * 用户登录
     * 验证用户名和密码，生成 JWT Token
     *
     * @param request 登录请求（用户名、密码）
     * @return 登录响应（包含 token 和用户信息）
     */
    public LoginResponse login(LoginRequest request) {
        String username = request.getUsername();
        String password = request.getPassword();

        // 检查账户是否被锁定
        Long lockTime = lockedAccounts.get(username);
        if (lockTime != null && (System.currentTimeMillis() - lockTime) < LOCK_DURATION_MS) {
            long remainingMinutes = (LOCK_DURATION_MS - (System.currentTimeMillis() - lockTime)) / 60000;
            throw new BusinessException(429, "账户已锁定，请 " + (remainingMinutes + 1) + " 分钟后重试");
        }
        // 清除过期锁定
        if (lockTime != null) {
            lockedAccounts.remove(username);
            loginFailCount.remove(username);
        }

        // 查找用户
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException("用户名或密码错误"));

        // 验证密码
        if (!PasswordUtil.verify(password, user.getPasswordHash())) {
            // 递增失败计数
            int fails = loginFailCount.merge(username, 1, Integer::sum);
            if (fails >= MAX_LOGIN_ATTEMPTS) {
                lockedAccounts.put(username, System.currentTimeMillis());
                loginFailCount.remove(username);
                throw new BusinessException(429, "登录失败次数过多，账户已锁定 15 分钟");
            }
            throw new BusinessException("用户名或密码错误");
        }

        // 登录成功，清除失败计数
        loginFailCount.remove(username);
        lockedAccounts.remove(username);

        // 生成 JWT Token
        String token = jwtTokenProvider.generateToken(user);
        log.info("用户登录成功: {}", username);

        return new LoginResponse(token, convertToUserInfo(user));
    }

    /**
     * 根据用户ID获取用户信息
     *
     * @param id 用户ID
     * @return 用户信息
     */
    public UserInfo getUserById(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new BusinessException("用户不存在"));
        return convertToUserInfo(user);
    }

    /**
     * 获取用户信息（供 Controller 的 getProfile 调用）
     *
     * @param userId 用户ID
     * @return 用户信息
     */
    public UserInfo getProfile(Long userId) {
        return getUserById(userId);
    }

    /**
     * 分页获取用户列表
     *
     * @param tenantId 租户ID
     * @param page     页码（从0开始）
     * @param size     每页大小
     * @return 用户分页列表
     */
    public Page<User> getUserList(String tenantId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        if (tenantId != null && !tenantId.isEmpty()) {
            return userRepository.findByTenantId(tenantId, pageable);
        }
        return userRepository.findAll(pageable);
    }

    /**
     * 更新用户角色
     * 仅允许设置为 admin 或 user 角色
     *
     * @param userId 用户ID
     * @param role   新角色
     * @return 更新后的用户信息
     */
    @Transactional
    public UserInfo updateUserRole(Long userId, String role) {
        if (!ALLOWED_ROLES.contains(role)) {
            throw new BusinessException("无效的用户角色，仅允许: admin, user");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("用户不存在"));

        user.setRole(role);
        userRepository.save(user);
        log.info("用户 {} 的角色已更新为: {}", user.getUsername(), role);

        return convertToUserInfo(user);
    }

    /**
     * 更新用户状态
     *
     * @param userId 用户ID
     * @param status 用户状态（enabled/disabled）
     */
    @Transactional
    public void updateUserStatus(Long userId, String status) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("用户不存在"));

        // 状态校验
        if (!"enabled".equals(status) && !"disabled".equals(status)) {
            throw new BusinessException("无效的用户状态，仅允许: enabled, disabled");
        }

        user.setStatus(status);
        userRepository.save(user);
        log.info("用户 {} 的状态已更新为: {}", user.getUsername(), status);
    }

    /**
     * 修改用户密码
     * 需要验证旧密码，新密码最少6个字符
     *
     * @param userId  用户ID
     * @param request 修改密码请求（原密码、新密码）
     */
    @Transactional
    public void changePassword(Long userId, ChangePasswordRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("用户不存在"));

        String oldPassword = request.getOldPassword();
        String newPassword = request.getNewPassword();

        // 验证旧密码
        if (!PasswordUtil.verify(oldPassword, user.getPasswordHash())) {
            throw new BusinessException("旧密码错误");
        }

        // 检查新密码长度
        if (newPassword.length() < 6) {
            throw new BusinessException("新密码长度不能少于6个字符");
        }

        // 更新密码
        user.setPasswordHash(PasswordUtil.hash(newPassword));
        userRepository.save(user);
        log.info("用户 {} 的密码已修改", user.getUsername());
    }

    /**
     * 获取当前登录用户信息
     *
     * @param userId 用户ID
     * @return 用户信息
     */
    public UserInfo getCurrentUser(Long userId) {
        return getUserById(userId);
    }

    /**
     * 将 User 实体转换为 UserInfo DTO
     *
     * @param user 用户实体
     * @return 用户信息 DTO
     */
    private UserInfo convertToUserInfo(User user) {
        return new UserInfo(user.getId(), user.getUsername(), user.getRole(), user.getTenantId());
    }
}
