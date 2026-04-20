package com.docgen.dto;

import lombok.Data;

import java.util.List;

/**
 * 模板字段定义 DTO
 */
@Data
public class TemplateFieldDTO {

    /**
     * 字段名（对应模板中的占位符名称，如 ${name} 中的 name）
     */
    private String name;

    /**
     * 字段标签（显示名称）
     */
    private String label;

    /**
     * 字段类型: text, number, date, select, textarea
     */
    private String type;

    /**
     * 是否必填
     */
    private Boolean required;

    /**
     * 选项列表（用于 select 类型）
     */
    private List<String> options;

    /**
     * 默认值
     */
    private String defaultValue;

}
