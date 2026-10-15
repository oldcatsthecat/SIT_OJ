package org.example.common.security;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

public final class SubmissionInput {
    private SubmissionInput() {}
    public static String language(String value) {
        if (value == null) throw badRequest("请选择语言");
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "cpp", "c++" -> "cpp";
            case "c" -> "c";
            case "py", "python" -> "python";
            case "java" -> "java";
            default -> throw badRequest("不支持的提交语言");
        };
    }
    public static void validate(Integer problemId, String code) {
        if (problemId == null || problemId <= 0) throw badRequest("题目编号无效");
        if (code == null || code.isBlank() || code.getBytes(StandardCharsets.UTF_8).length > 262144) {
            throw badRequest("代码不能为空且不能超过256KB");
        }
    }
    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
