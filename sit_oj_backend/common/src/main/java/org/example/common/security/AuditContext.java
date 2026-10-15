package org.example.common.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import java.util.LinkedHashMap;
import java.util.Map;

/** Explicit allowlist: never cache request bodies, passwords, source code or complete tokens. */
public final class AuditContext {
    public static final String REQUEST_ID = AuditContext.class.getName() + ".requestId";
    static final String DETAILS = AuditContext.class.getName() + ".details";
    static final String IDENTITY_VERIFIER = AuditContext.class.getName() + ".verifier";
    private AuditContext() { }
    public static HttpServletRequest currentRequest() {
        return RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes
                ? attributes.getRequest() : null;
    }
    @SuppressWarnings("unchecked")
    public static Map<String, Object> details(HttpServletRequest request) {
        if (request == null) return new LinkedHashMap<>();
        Object existing = request.getAttribute(DETAILS);
        if (existing instanceof Map<?, ?>) return (Map<String, Object>) existing;
        Map<String, Object> result = new LinkedHashMap<>();
        request.setAttribute(DETAILS, result);
        return result;
    }
    public static void submissionAttempt(HttpServletRequest request, Object claimedUserId, Object problemId,
                                         Object competitionId, Object language) {
        var data = details(request);
        data.put("operation", "SUBMIT");
        data.put("claimedUserId", numericId(claimedUserId));
        data.put("problemId", numericId(problemId));
        data.put("competitionId", numericId(competitionId));
        data.put("language", language instanceof String value ? AuditIdentity.bounded(value, 32) : null);
    }
    public static void submissionSaved(HttpServletRequest request, Object submissionId, Object submittedUserId) {
        details(request).put("submissionId", numericId(submissionId));
        details(request).put("submittedUserId", numericId(submittedUserId));
    }
    public static Long numericId(Object value) {
        if (!(value instanceof Number) && !(value instanceof String)) return null;
        try { return Long.valueOf(value.toString()); } catch (NumberFormatException invalid) { return null; }
    }
}
