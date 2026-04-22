package com.docgen.controller;

import com.docgen.dto.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 健康检查控制器
 * 提供系统健康状态检查接口
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class HealthController {

    /**
     * 健康检查
     * 此接口无需认证，已在 SecurityConfig 白名单中配置
     *
     * @return 系统健康状态信息
     */
    @GetMapping("/health")
    public Result<Map<String, Object>> health() {
        Map<String, Object> healthInfo = Map.of(
                "status", "UP",
                "timestamp", LocalDateTime.now().toString()
        );
        return Result.success(healthInfo);
    }
}
