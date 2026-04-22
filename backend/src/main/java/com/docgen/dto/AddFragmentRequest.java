package com.docgen.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 添加片段请求 DTO
 * 用于向文档组合中添加单个片段
 */
@Data
public class AddFragmentRequest {

    /**
     * 片段ID
     */
    @NotNull(message = "片段ID不能为空")
    private Long fragmentId;

    /**
     * 章节标题
     */
    private String sectionTitle;

    /**
     * 是否启用，默认为 true
     */
    private Boolean enabled = true;
}
