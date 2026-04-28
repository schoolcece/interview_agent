package com.ai.interview.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 健康检查接口（不需要认证）。
 * <p>供 Docker Compose healthcheck、Kubernetes liveness probe 及负载均衡器探活使用。
 */
@RestController
public class HealthController {

    /** 始终返回 {@code {"status":"ok"}}，HTTP 200 表示服务正常。 */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "ok"));
    }
}
