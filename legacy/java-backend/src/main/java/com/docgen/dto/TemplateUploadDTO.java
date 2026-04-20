package com.docgen.dto;

import lombok.Data;

import java.util.List;

/**
 * 模板上传 DTO
 */
@Data
public class TemplateUploadDTO {

    /**
     * 模板名称
     */
    private String name;

    /**
     * 描述
     */
    private String description;

    /**
     * 分类
     */
    private String category;

    /**
     * 字段定义列表
     */
    private List<TemplateFieldDTO> fields;

}
