package org.example.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JudgeServerResponse<T> {
    // 判题机返回的错误码，成功时为 null
    private String err;

    // 判题机返回的具体数据（成功时是数组，失败时是字符串原因）
    private T data;
}