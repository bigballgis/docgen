package com.docgen.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * 模板组合关系实体类（模板与片段的关联）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "template_compositions")
public class TemplateComposition {

    /** 主键ID */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 模板ID */
    @Column(name = "template_id")
    private Long templateId;

    /** 片段ID */
    @Column(name = "fragment_id")
    private Long fragmentId;

    /** 排序顺序，默认为 0 */
    @Column(name = "sort_order")
    @Builder.Default
    private Integer sortOrder = 0;

    /** 章节标题，默认为空字符串 */
    @Column(name = "section_title")
    @Builder.Default
    private String sectionTitle = "";

    /** 是否启用，默认为 true */
    @Builder.Default
    private Boolean enabled = true;

    /** 创建时间 */
    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
