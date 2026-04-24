package com.ai.interview.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.Map;

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
     * Call Planning Agent Service
     */
    public Map<String, Object> callPlanningService(Map<String, Object> request) {
        return postToAgent(planningEndpoint, request, "Planning");
    }

    /**
     * Call Interview Agent Service
     */
    public Map<String, Object> callInterviewService(Map<String, Object> request) {
        return postToAgent(interviewEndpoint, request, "Interview");
    }

    /**
     * Call Evaluation Agent Service
     */
    public Map<String, Object> callEvaluationService(Map<String, Object> request) {
        return postToAgent(evaluationEndpoint, request, "Evaluation");
    }

    /**
     * Call Memory Agent Service
     */
    public Map<String, Object> callMemoryService(Map<String, Object> request) {
        return postToAgent(memoryEndpoint, request, "Memory");
    }

    /**
     * Call Reflection Agent Service
     */
    public Map<String, Object> callReflectionService(Map<String, Object> request) {
        return postToAgent(reflectionEndpoint, request, "Reflection");
    }

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
