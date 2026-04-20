package com.docgen.repository;

import com.docgen.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * 用户数据访问接口
 */
public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * 根据用户名查找用户
     */
    Optional<User> findByUsername(String username);

    /**
     * 根据用户名和租户ID查找用户
     */
    Optional<User> findByUsernameAndTenantId(String username, String tenantId);

}
