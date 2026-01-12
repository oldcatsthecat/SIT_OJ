package org.example.problem.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "oj.testcase")
public class TestcaseConfig {
    private String remoteHost;
    private String remotePath;
    private String remoteUser;
    private String remotePassword;
    private String localBasePath;
    private boolean enableRemoteSync;

}