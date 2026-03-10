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
    private Object language_config;

    @JsonProperty("max_cpu_time")
    private Integer max_cpu_time;

    @JsonProperty("max_memory")
    private Integer max_memory;

    @JsonProperty("spj_src")
    private String spj_src;

    private Boolean output;
}
