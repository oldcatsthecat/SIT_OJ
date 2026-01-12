package org.example.judge.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "oj.judge")
public class JudgeConfig {
    private String serverUrl;
    private String token;
    private JavaConfig java;

    @Data
    public static class JavaConfig {
        private int maxCpuTime;
        private int maxRealTime;
        private long maxMemory;
    }
}