package org.example.competition.dto;

import lombok.Data;

import java.util.HashMap;
import java.util.Map;

@Data
public class ResolverExportRequest {
    // 用户 ID -> participants / stars；未指定的用户沿用 participants。
    private Map<Integer, String> userGroups = new HashMap<>();
}
