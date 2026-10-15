package org.example.common.security;

import org.example.common.utils.Result;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;
import java.util.Map;

@RestControllerAdvice
public class AuditResultAdvice implements ResponseBodyAdvice<Object> {
    @Override public boolean supports(MethodParameter type, Class<? extends HttpMessageConverter<?>> converter) { return true; }
    @Override public Object beforeBodyWrite(Object body, MethodParameter type, MediaType contentType,
            Class<? extends HttpMessageConverter<?>> converter, ServerHttpRequest request, ServerHttpResponse response) {
        if (body instanceof Result<?> result && request instanceof ServletServerHttpRequest servlet) {
            var req = servlet.getServletRequest();
            var details = AuditContext.details(req);
            details.put("businessCode", result.getCode());
            if (req.getRequestURI().endsWith("/submit") && result.getData() instanceof Map<?, ?> data
                    && Integer.valueOf(200).equals(result.getCode())) {
                details.put("submissionId", AuditContext.numericId(data.get("submissionId")));
            }
            if (("/users/login".equals(req.getRequestURI()) || "/users/refresh".equals(req.getRequestURI()))
                    && Integer.valueOf(200).equals(result.getCode()) && result.getData() instanceof String token
                    && req.getAttribute(AuditContext.IDENTITY_VERIFIER) instanceof AuditIdentity verifier) {
                var issued = verifier.inspect(token);
                if ("VALID".equals(issued.status())) {
                    details.put("issuedTokenFingerprint", issued.fingerprint());
                    details.put("issuedUserId", issued.userId());
                    details.put("issuedUsername", issued.username());
                }
            }
        }
        return body;
    }
}
