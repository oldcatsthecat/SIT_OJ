package org.example.common.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JudgeServerRequestSpj {
    private String src;

    @JsonProperty("language_config")
    private Object languageConfig;

    @JsonProperty("max_cpu_time")
    private Integer maxCpuTime;

    @JsonProperty("max_memory")
    private Integer maxMemory;

    @JsonProperty("spj_version")
    private String spjVersion;

    @JsonProperty("spj_config")
    private Object spjConfig;

    @JsonProperty("spj_compile_config")
    private Object spjCompileConfig;

    @JsonProperty("spj_src")
    private String spjSrc; // 建议改名

    @JsonProperty("test_case_id")
    private String testCaseId;

    private Boolean output;
}