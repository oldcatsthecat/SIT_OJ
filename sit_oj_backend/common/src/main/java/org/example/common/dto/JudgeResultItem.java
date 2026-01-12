package org.example.common.dto;

import lombok.Data;

@Data
public class JudgeResultItem {
    private Integer cpu_time;
    private Integer result;    // 0 是成功, -1 是 WA, 1,2 TLE, 3 MLE, 4 RE, 5 SE
    private Integer memory;
    private Integer real_time;
    private Integer signal;
    private Integer error;
    private Integer exit_code;
    private String output_md5;
    private String test_case;

}