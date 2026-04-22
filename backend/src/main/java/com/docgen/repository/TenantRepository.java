package com.docgen.repository;

import com.docgen.entity.Tenant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * 租户数据访问层
 */
public interface TenantRepository extends JpaRepository<Tenant, Long> {

    /**
     * 根据租户编码查询租户
     *
     * @param code 租户编码
     * @return 租户信息
     */
    Optional<Tenant> findByCode(String code);

    /**
     * 检查租户名称是否已存在
     *
     * @param name 租户名称
     * @return 是否存在
     */
    boolean existsByName(String name);

    /**
     * 检查租户编码是否已存在
     *
     * @param code 租户编码
     * @return 是否存在
     */
    boolean existsByCode(String code);
}
