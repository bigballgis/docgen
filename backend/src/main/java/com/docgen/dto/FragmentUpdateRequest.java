package com.docgen.dto;

import lombok.Data;

import java.util.List;

/**
 * 片段更新请求 DTO
 * 用于更新已有的文档片段，所有字段均为可选
 */
@Data
public class FragmentUpdateRequest {

    /**
     * 片段名称
     */
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
    private String content;

    /**
     * 变更说明
     */
    private String changeNote;
}
