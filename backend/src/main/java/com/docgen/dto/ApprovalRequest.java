package com.docgen.dto;

import lombok.Data;

/**
 * 审批请求 DTO
 * 用于文档审批操作（通过/拒绝）
 */
@Data
public class ApprovalRequest {

    /**
     * 审批意见
     */
    private String comment;

    /**
     * 审批原因
     */
    private String reason;
}
