package com.docgen.repository;

import com.docgen.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 用户数据访问层
 */
public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * 根据用户名查询用户
     *
     * @param username 用户名
     * @return 用户信息
     */
    Optional<User> findByUsername(String username);

    /**
     * 根据租户ID查询用户列表
     *
     * @param tenantId 租户ID
     * @return 该租户下的用户列表
     */
    List<User> findByTenantId(String tenantId);

    /**
     * 根据租户ID查询用户列表（分页）
     *
     * @param tenantId 租户ID
     * @param pageable 分页参数
     * @return 用户分页列表
     */
    Page<User> findByTenantId(String tenantId, Pageable pageable);

    /**
     * 统计指定租户下的用户总数
     *
     * @param tenantId 租户ID
     * @return 用户数量
     */
    long countByTenantId(String tenantId);
}
