package org.example.submission.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 【已废弃】异步判题线程池配置
 *
 * 原用于 @Async("judgeTaskExecutor") + Feign 调用 Judge 模块进行判题。
 * 现已迁移至 RabbitMQ 消息队列（参见：
 *   - JudgeProducer: 发送判题请求到 RabbitMQ
 *   - JudgeResultConsumer: 消费判题结果
 *   - judge 模块 JudgeRequestConsumer: 消费判题请求
 * ）
 *
 * 保留此文件仅作参考，不在系统中使用。
 */
@Configuration
@EnableAsync
@Deprecated
public class AsyncConfig {

    @Bean("judgeTaskExecutor")
    @Deprecated
    public Executor judgeTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("JudgeWorker-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
