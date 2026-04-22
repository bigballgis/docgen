package com.docgen.dto;

import lombok.Data;

import java.util.List;

/**
 * 保存组合请求 DTO
 * 用于保存文档组合中的片段列表
 */
@Data
public class SaveCompositionRequest {

    /**
     * 组合项列表
     */
    private List<CompositionItem> items;
}
