package com.jobtrack.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Enables the AI/Gmail settings and the scheduler that runs the periodic Gmail sync. */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@EnableConfigurationProperties({AiProperties.class, GmailProperties.class})
public class AiConfig {
}
