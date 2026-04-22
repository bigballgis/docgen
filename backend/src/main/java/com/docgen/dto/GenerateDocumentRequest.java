package com.docgen.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.util.Map;

/**
 * 生成文档请求 DTO
 */
@Data
public class GenerateDocumentRequest {

    /**
     * 模板ID
     */
    @NotNull(message = "模板ID不能为空")
    private Long templateId;

    /**
     * 模板字段数据（键值对）
     */
    private Map<String, Object> fields;

    /**
     * 输出格式，默认为 "docx"
     */
    @Pattern(regexp = "docx|pdf", message = "格式必须为 docx 或 pdf")
    private String format = "docx";
}
