package com.docgen.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * 模板审批实体类
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "template_approvals")
public class TemplateApproval {

    /** 主键ID */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 模板ID */
    @Column(name = "template_id")
    private Long templateId;

    /** 审批动作（approve/reject） */
    private String action;

    /** 审批人ID */
    @Column(name = "reviewer_id")
    private Long reviewerId;

    /** 审批意见 */
    private String comment;

    /** 审批状态，默认为 "pending" */
    @Builder.Default
    private String status = "pending";

    /** 创建时间 */
    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
