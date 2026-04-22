package com.docgen.entity;

import jakarta.persistence.Index;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * 文档实体类
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "documents", indexes = {
    @Index(name = "idx_doc_tenant_status", columnList = "tenant_id, status, deleted_at"),
    @Index(name = "idx_doc_create_time", columnList = "created_at DESC"),
    @Index(name = "idx_doc_file_key", columnList = "file_key")
})
public class Document {

    /** 主键ID */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 模板ID */
    @Column(name = "template_id")
    private Long templateId;

    /** 模板名称 */
    @Column(name = "template_name")
    private String templateName;

    /** 文件存储键 */
    @Column(name = "file_key")
    private String fileKey;

    /** 文件名 */
    @Column(name = "file_name")
    private String fileName;

    /** 输出格式，默认为 "docx" */
    @Column(name = "output_format")
    @Builder.Default
    private String outputFormat = "docx";

    /** 状态，默认为 "pending" */
    @Builder.Default
    private String status = "pending";

    /** 创建用户ID */
    @Column(name = "user_id")
    private Long userId;

    /** 租户ID，默认为 "default" */
    @Column(name = "tenant_id")
    @Builder.Default
    private String tenantId = "default";

    /** 删除时间（软删除标记） */
    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    /** 创建时间 */
    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
