package com.busymumkitchen.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

/**
 * When {@code rabbitmq.enabled=false} (the default for local dev), this
 * configuration is loaded instead of {@link RabbitMQConfig}. No queues,
 * exchanges or listeners are created and the application starts without
 * a running RabbitMQ broker.
 *
 * In Docker / production, set {@code RABBITMQ_ENABLED=true} so the real
 * {@link RabbitMQConfig} and message listeners are activated instead.
 */
@Configuration
@ConditionalOnProperty(name = "rabbitmq.enabled", havingValue = "false", matchIfMissing = true)
@Slf4j
public class RabbitMQDisabledConfig {

    public RabbitMQDisabledConfig() {
        log.info("[DEV MODE] RabbitMQ is DISABLED. Set RABBITMQ_ENABLED=true to enable messaging.");
    }
}
