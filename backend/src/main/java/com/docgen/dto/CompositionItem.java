package com.docgen.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 组合项 DTO
 * 表示文档组合中的单个片段项
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CompositionItem {

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
     * 是否启用
     */
    private Boolean enabled;
}
