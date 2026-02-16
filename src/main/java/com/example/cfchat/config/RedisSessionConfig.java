package com.example.cfchat.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
// Note: @EnableRedisHttpSession is not used directly - it's enabled via spring.session.store-type=redis
// This config serves as a marker and any additional Redis session customization

@Configuration
@ConditionalOnProperty(name = "spring.session.store-type", havingValue = "redis")
public class RedisSessionConfig {
    // Redis sessions auto-configured by spring-session-data-redis when store-type=redis
    // Session timeout configured in application.yml: server.servlet.session.timeout
}
