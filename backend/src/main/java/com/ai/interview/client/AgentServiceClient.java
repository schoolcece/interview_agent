package com.ai.interview.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.Map;

/**
 * Python Agent 服务的 HTTP 客户端，封装与 Python FastAPI 的所有通信。
 *
 * <p>各方法对应 Python 侧的 FastAPI 路由：
 * <ul>
 *   <li>{@link #callPlanningService}   → POST /api/v1/interview/planning</li>
 *   <li>{@link #callInterviewService}  → POST /api/v1/interview/interview</li>
 *   <li>{@link #callEvaluationService} → POST /api/v1/interview/evaluation</li>
 *   <li>{@link #callMemoryService}     → POST /api/v1/interview/memory（预留）</li>
 *   <li>{@link #callReflectionService} → POST /api/v1/interview/reflection（预留）</li>
 * </ul>
 *
 * <p>使用 Spring WebFlux 的 {@link WebClient} 发起同步阻塞调用（{@code .block()}），
 * 超时时间由 {@code agent.service.timeout}（毫秒）配置。
 * Phase 1 后若需流式响应（SSE / streaming），可改为 {@code .bodyToFlux()}。
 */
@Component
@Slf4j
public class AgentServiceClient {

    private final WebClient webClient;

    @Value("${agent.service.url}")
    private String agentServiceUrl;

    @Value("${agent.service.planning.endpoint}")
    private String planningEndpoint;

    @Value("${agent.service.interview.endpoint}")
    private String interviewEndpoint;

    @Value("${agent.service.evaluation.endpoint}")
    private String evaluationEndpoint;

    @Value("${agent.service.memory.endpoint}")
    private String memoryEndpoint;

    @Value("${agent.service.reflection.endpoint}")
    private String reflectionEndpoint;

    @Value("${agent.service.timeout}")
    private long timeout;

    public AgentServiceClient(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.build();
    }

    /**
     * 调用 Planning 服务：根据用户简历和面试配置，让 AI 生成面试计划（题目列表、知识点覆盖等）。
     *
     * @param request 至少包含 user_id、session_id、config 字段的 Map
     * @return Python 返回的计划内容 Map
     */
    public Map<String, Object> callPlanningService(Map<String, Object> request) {
        return postToAgent(planningEndpoint, request, "Planning");
    }

    /**
     * 调用 Interview 服务：对用户本轮回答进行评分，并生成下一个问题或结束信号。
     *
     * @param request 含 session_id、user_response、current_turn 等字段的 Map
     * @return 含 score、next_question、is_complete 等字段的 Map
     */
    public Map<String, Object> callInterviewService(Map<String, Object> request) {
        return postToAgent(interviewEndpoint, request, "Interview");
    }

    /**
     * 调用 Evaluation 服务：对整场面试生成综合评估报告（总分、薄弱点、建议）。
     *
     * @param request 含 session_id、user_id 的 Map
     * @return 评估报告 Map
     */
    public Map<String, Object> callEvaluationService(Map<String, Object> request) {
        return postToAgent(evaluationEndpoint, request, "Evaluation");
    }

    /**
     * 调用 Memory 服务（预留）：将本次面试经验写入用户长期记忆，用于后续自适应出题。
     */
    public Map<String, Object> callMemoryService(Map<String, Object> request) {
        return postToAgent(memoryEndpoint, request, "Memory");
    }

    /**
     * 调用 Reflection 服务（预留）：反思并更新用户知识掌握度模型。
     */
    public Map<String, Object> callReflectionService(Map<String, Object> request) {
        return postToAgent(reflectionEndpoint, request, "Reflection");
    }

    /**
     * 通用 POST 方法：向 Python Agent 的指定端点发送请求并同步等待响应。
     *
     * @param endpoint    相对路径，如 "/api/v1/interview/planning"
     * @param request     请求体（自动序列化为 JSON）
     * @param serviceName 日志标识名称
     * @return 反序列化后的响应 Map
     * @throws RuntimeException 调用失败或返回为空时抛出
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> postToAgent(String endpoint, Map<String, Object> request, String serviceName) {
        try {
            Map<String, Object> response = webClient.post()
                    .uri(agentServiceUrl + endpoint)
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block(Duration.ofMillis(timeout));
            if (response == null) {
                throw new RuntimeException(serviceName + " service returned empty response");
            }
            return response;
        } catch (Exception e) {
            log.error("Error calling {} service: {}", serviceName, e.getMessage(), e);
            throw new RuntimeException(serviceName + " service call failed", e);
        }
    }
}
