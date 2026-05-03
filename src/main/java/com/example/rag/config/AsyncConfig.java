package com.example.rag.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

// Enables @Async support and defines a dedicated thread pool for ingestion tasks.
// Ingestion is CPU/network-heavy (Tika parsing + embedding API calls), so it gets
// its own pool to avoid starving the web thread pool that handles regular requests.
@Configuration
@EnableAsync
@EnableConfigurationProperties(RagProperties.class)
public class AsyncConfig {

    private static final Logger log = LoggerFactory.getLogger(AsyncConfig.class);

    private final RagProperties.Async asyncProps;

    public AsyncConfig(RagProperties ragProperties) {
        this.asyncProps = ragProperties.ingestion().async();
    }

    @Bean(name = "ingestionExecutor")
    public Executor ingestionExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(asyncProps.corePoolSize());
        executor.setMaxPoolSize(asyncProps.maxPoolSize());
        executor.setQueueCapacity(asyncProps.queueCapacity());
        executor.setThreadNamePrefix("ingestion-");
        executor.initialize();
        return executor;
    }

    // Called when an @Async void method throws — since the exception can't propagate
    // back to the caller, we log it here so it isn't silently swallowed.
    @Bean
    public AsyncUncaughtExceptionHandler asyncUncaughtExceptionHandler() {
        return (throwable, method, params) ->
                log.error("Async ingestion error in {}: {}", method.getName(), throwable.getMessage(), throwable);
    }
}
