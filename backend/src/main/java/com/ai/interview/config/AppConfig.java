package com.ai.interview.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * 应用级通用 Bean 配置。
 */
@Configuration
public class AppConfig {

    /**
     * 全局 Jackson ObjectMapper，注册了 Java 8 时间模块（处理 Instant / LocalDateTime 等），
     * 并禁用将日期序列化为时间戳的默认行为（改为 ISO-8601 字符串）。
     */
    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }

    /**
     * WebClient.Builder Bean，供 {@link com.ai.interview.client.AgentServiceClient} 注入使用。
     * 使用 Builder 而非直接注入 WebClient，以便各使用方可自行追加 baseUrl / filter 等配置。
     */
    @Bean
    public WebClient.Builder webClientBuilder() {
        return WebClient.builder();
    }
}
