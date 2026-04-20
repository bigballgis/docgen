package com.docgen.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Map;

/**
 * 文档生成请求 DTO
 */
@Data
public class GenerateRequest {

    /**
     * 模板 ID
     */
    @NotNull(message = "模板ID不能为空")
    private Long templateId;

    /**
     * 填充数据（key 对应模板中的占位符名称）
     */
    @NotNull(message = "填充数据不能为空")
    private Map<String, Object> data;

    /**
     * 输出格式: docx, pdf
     */
    private String outputFormat = "docx";

}
