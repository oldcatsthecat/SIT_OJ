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
    private Object language_config; // 变量名改为下划线

    @JsonProperty("max_cpu_time")
    private Integer max_cpu_time;

    @JsonProperty("max_memory")
    private Integer max_memory;

    @JsonProperty("spj_version")
    private String spj_version;

    @JsonProperty("spj_config")
    private Object spj_config;

    @JsonProperty("spj_compile_config")
    private Object spj_compile_config;

    @JsonProperty("spj_src")
    private String spj_src;

    @JsonProperty("test_case_id")
    private String test_case_id;

    private Boolean output;
}