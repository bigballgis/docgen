package com.docgen.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * 租户实体类
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "tenants")
public class Tenant {

    /** 主键ID */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 租户名称，唯一且不可为空 */
    @Column(unique = true, nullable = false)
    private String name;

    /** 租户编码，唯一且不可为空 */
    @Column(unique = true, nullable = false)
    private String code;

    /** 状态，默认为 "active" */
    @Builder.Default
    private String status = "active";

    /** 租户配置（JSON格式） */
    private String config;

    /** 创建时间 */
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    /** 更新时间 */
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
