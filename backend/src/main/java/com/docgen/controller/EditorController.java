package com.docgen.controller;

import com.docgen.dto.EditorCallbackRequest;
import com.docgen.dto.Result;
import com.docgen.service.EditorService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 编辑器控制器
 * 处理在线编辑器的配置获取、保存回调、文档下载等操作
 */
@RestController
@RequestMapping("/api/v1/editor")
@RequiredArgsConstructor
public class EditorController {

    private final EditorService editorService;

    /**
     * 获取编辑器配置
     * 根据文件键获取编辑器的初始化配置信息
     * 此接口为可选认证，已在 SecurityConfig 白名单中配置
     *
     * @param fileKey 文件存储键
     * @return 编辑器配置信息
     */
    @GetMapping("/config/{fileKey}")
    public Result<Map<String, Object>> getEditorConfig(@PathVariable String fileKey) {
        Map<String, Object> config = editorService.getEditorConfig(fileKey);
        return Result.success(config);
    }

    /**
     * 接收编辑保存回调
     * 编辑器保存文件后回调此接口，更新服务器上的文件
     *
     * @param callbackData 回调数据（包含文件信息、保存结果等）
     * @return 操作结果
     */
    @PostMapping("/callback")
    public Result<Void> editorCallback(@RequestBody EditorCallbackRequest callbackData) {
        editorService.handleCallback(callbackData);
        return Result.success(null, "编辑保存回调处理成功");
    }

    /**
     * 下载编辑器文档
     * 根据文件键下载编辑器中的文档文件
     *
     * @param fileKey  文件存储键
     * @param response HTTP 响应对象
     */
    @GetMapping("/download/{fileKey}")
    public void downloadEditorFile(
            @PathVariable String fileKey,
            HttpServletResponse response) {
        editorService.downloadFile(fileKey, response);
    }
}
