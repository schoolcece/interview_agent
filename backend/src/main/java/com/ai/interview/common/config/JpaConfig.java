package com.ai.interview.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * 启用 Spring Data JPA 审计 (@CreatedDate / @LastModifiedDate)。
 */
@Configuration
@EnableJpaAuditing
public class JpaConfig {
}
