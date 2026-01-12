// TestCaseInfo.java
package org.example.problem.entity;

import lombok.Data;
import java.util.Map;

@Data
public class TestCaseInfo {
    private boolean spj = false;
    private Map<String, TestCaseDetail> test_cases;
}
