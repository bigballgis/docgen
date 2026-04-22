package com.docgen.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * 片段版本实体类
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "fragment_versions")
public class FragmentVersion {

    /** 主键ID */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 片段ID */
    @Column(name = "fragment_id")
    private Long fragmentId;

    /** 版本号 */
    private Integer version;

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

    /** 变更说明，默认为空字符串 */
    @Column(name = "change_note")
    @Builder.Default
    private String changeNote = "";

    /** 创建人ID */
    @Column(name = "created_by")
    private Long createdBy;

    /** 创建时间 */
    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
