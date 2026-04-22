package com.docgen.entity;

import jakarta.persistence.Index;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * 片段实体类
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "fragments", indexes = {
    @Index(name = "idx_fragment_tenant_category", columnList = "tenant_id, category, deleted_at"),
    @Index(name = "idx_fragment_tenant_status", columnList = "tenant_id, status, deleted_at"),
    @Index(name = "idx_fragment_create_time", columnList = "created_at DESC")
})
public class Fragment {

    /** 主键ID */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 片段名称 */
    private String name;

    /** 片段描述，默认为空字符串 */
    @Builder.Default
    private String description = "";

    /** 片段分类，默认为空字符串 */
    @Builder.Default
    private String category = "";

    /** HTML格式内容，默认为空字符串 */
    @Column(name = "content_html")
    @Builder.Default
    private String contentHtml = "";

    /** Docx文件存储路径，默认为空字符串 */
    @Column(name = "content_docx_path")
    @Builder.Default
    private String contentDocxPath = "";

    /** 片段字段定义（JSON数组），默认为空数组 */
    @Builder.Default
    private String fields = "[]";

    /** 标签（JSON数组），默认为空数组 */
    @Builder.Default
    private String tags = "[]";

    /** 租户ID，默认为 "default" */
    @Column(name = "tenant_id")
    @Builder.Default
    private String tenantId = "default";

    /** 状态，默认为 "draft" */
    @Builder.Default
    private String status = "draft";

    /** 当前版本号，默认为 1 */
    @Column(name = "current_version")
    @Builder.Default
    private Integer currentVersion = 1;

    /** 创建人ID */
    @Column(name = "created_by")
    private Long createdBy;

    /** 创建时间 */
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    /** 更新时间 */
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /** 删除时间（软删除标记） */
    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;
}
