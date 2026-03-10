package org.example.problem.entity;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

// TestCaseDetail.java
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TestCaseDetail {
    private String input_name;
    private String output_name;
    private String stripped_output_md5;
    private Long input_size;
    private Long output_size;
}
