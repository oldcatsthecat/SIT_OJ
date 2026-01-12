package org.example.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class JudgeResultResponse {
    private String status;         // AC, WA, CE, TLE 等
    private String errorMessage;   // 编译或系统错误详情
    private Integer timeCost;      // 最大耗时(ms)
    private Integer memoryCost;    // 最大内存(KB)
    private List<JudgeResultItem> details; // 测试点对象列表
    private String judgeInfo;      // 用于存入数据库的 JSON 字符串详情
}