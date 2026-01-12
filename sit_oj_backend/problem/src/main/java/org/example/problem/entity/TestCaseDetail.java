package org.example.problem.entity;

import lombok.Data;

// TestCaseDetail.java
@Data
public class TestCaseDetail {
    private String input_name;
    private String output_name;
    private String stripped_output_md5;
    private long input_size;
    private long output_size;
}
