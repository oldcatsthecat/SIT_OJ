package org.example.submission.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

// 路径：com.yourname.submissions.config.AsyncConfig
@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean("judgeTaskExecutor")
    public Executor judgeTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(2); // 你的 2 核 CPU 限制
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("JudgeWorker-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}