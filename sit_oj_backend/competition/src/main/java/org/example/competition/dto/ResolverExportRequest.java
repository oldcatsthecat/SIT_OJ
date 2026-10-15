package org.example.competition.dto;

import lombok.Data;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
public class ResolverExportRequest {
    // 用户 ID -> participants / stars；未指定的用户沿用 participants。
    private Map<Integer, String> userGroups = new HashMap<>();
    // null 表示沿用完整报名名单；非 null 时只导出指定选手。
    private List<Integer> selectedUserIds;
}
