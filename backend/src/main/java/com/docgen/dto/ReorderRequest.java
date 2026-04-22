package com.docgen.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * 重新排序请求 DTO
 * 用于对文档组合中的片段进行重新排序
 */
@Data
public class ReorderRequest {

    /**
     * 按新顺序排列的片段ID列表
     */
    @NotEmpty(message = "片段ID列表不能为空")
    private List<Long> fragmentIds;
}
