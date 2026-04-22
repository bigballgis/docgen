package com.docgen.entity;

import jakarta.persistence.Index;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * 用户实体类
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "users", indexes = {
    @Index(name = "idx_user_tenant", columnList = "tenant_id"),
    @Index(name = "idx_user_tenant_status", columnList = "tenant_id, status")
})
public class User {

    /** 主键ID */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 用户名，唯一且不可为空 */
    @Column(unique = true, nullable = false)
    private String username;

    /** 密码哈希 */
    @Column(name = "password_hash")
    private String passwordHash;

    /** 角色，默认为 "user" */
    @Builder.Default
    private String role = "user";

    /** 租户ID，默认为 "default" */
    @Column(name = "tenant_id")
    @Builder.Default
    private String tenantId = "default";

    /** 用户状态，默认为 "enabled" */
    @Builder.Default
    private String status = "enabled";

    /** 创建时间 */
    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
