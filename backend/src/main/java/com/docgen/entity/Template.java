package com.docgen.entity;

import jakarta.persistence.Index;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * 模板实体类
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "templates", indexes = {
    @Index(name = "idx_template_tenant_status", columnList = "tenant_id, status, deleted_at"),
    @Index(name = "idx_template_tenant_category", columnList = "tenant_id, category, deleted_at"),
    @Index(name = "idx_template_create_time", columnList = "create_time DESC")
})
public class Template {

    /** 主键ID */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 模板名称 */
    private String name;

    /** 模板描述 */
    private String description;

    /** 文件存储名 */
    @Column(name = "file_name")
    private String fileName;

    /** 原始文件名 */
    @Column(name = "original_file_name")
    private String originalFileName;

    /** 模板字段定义（JSON字符串） */
    private String fields;

    /** 模板分类 */
    private String category;

    /** 创建用户ID */
    @Column(name = "user_id")
    private Long userId;

    /** 租户ID，默认为 "default" */
    @Column(name = "tenant_id")
    @Builder.Default
    private String tenantId = "default";

    /** 状态，默认为 "draft" */
    @Builder.Default
    private String status = "draft";

    /** 版本号，默认为 1 */
    @Builder.Default
    private Integer version = 1;

    /** 发布时间 */
    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    /** 审批人ID */
    @Column(name = "approved_by")
    private Long approvedBy;

    /** 审批时间 */
    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    /** 驳回原因 */
    @Column(name = "reject_reason")
    private String rejectReason;

    /** 删除时间（软删除标记） */
    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    /** 创建时间 */
    @Column(name = "create_time")
    private LocalDateTime createTime;

    /** 更新时间 */
    @Column(name = "update_time")
    private LocalDateTime updateTime;

    /** 当前版本号，默认为 1 */
    @Column(name = "current_version")
    @Builder.Default
    private Integer currentVersion = 1;
}
