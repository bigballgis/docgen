package com.docgen.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * 模板版本实体类
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "template_versions")
public class TemplateVersion {

    /** 主键ID */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 模板ID */
    @Column(name = "template_id")
    private Long templateId;

    /** 版本号 */
    private Integer version;

    /** 文件存储路径 */
    @Column(name = "file_path")
    private String filePath;

    /** 文件大小（字节） */
    @Column(name = "file_size")
    private Long fileSize;

    /** 变更说明 */
    @Column(name = "change_note")
    private String changeNote;

    /** 创建人ID */
    @Column(name = "created_by")
    private Long createdBy;

    /** 创建时间 */
    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
