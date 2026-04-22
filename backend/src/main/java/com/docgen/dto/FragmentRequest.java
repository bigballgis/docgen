package com.docgen.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

/**
 * 片段请求 DTO
 * 用于创建新的文档片段
 */
@Data
public class FragmentRequest {

    /**
     * 片段名称
     */
    @NotBlank(message = "片段名称不能为空")
    private String name;

    /**
     * 片段描述
     */
    private String description;

    /**
     * 片段分类
     */
    private String category;

    /**
     * 标签列表
     */
    private List<String> tags;

    /**
     * 片段内容
     */
    @NotBlank(message = "片段内容不能为空")
    private String content;
}
