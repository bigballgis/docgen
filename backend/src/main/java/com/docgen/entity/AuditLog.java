package com.docgen.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * 审计日志实体类
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "audit_logs")
public class AuditLog {

    /** 主键ID */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 操作用户ID */
    @Column(name = "user_id")
    private Long userId;

    /** 操作用户名 */
    private String username;

    /** 操作动作 */
    private String action;

    /** 资源类型 */
    private String resource;

    /** 资源ID */
    @Column(name = "resource_id")
    private String resourceId;

    /** 操作IP地址 */
    private String ip;

    /** 用户代理（浏览器信息） */
    @Column(name = "user_agent")
    private String userAgent;

    /** 操作详情 */
    private String details;

    /** 创建时间 */
    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
