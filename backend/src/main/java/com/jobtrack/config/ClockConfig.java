package com.jobtrack.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Services read "now" from an injected clock so that time-dependent rules (upcoming interviews,
 * "this month" statistics, overdue tasks) can be tested deterministically.
 */
@Configuration(proxyBeanMethods = false)
public class ClockConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
