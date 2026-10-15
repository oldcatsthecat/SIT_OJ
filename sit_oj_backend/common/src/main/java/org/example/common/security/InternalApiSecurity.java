package org.example.common.security;

import feign.RequestInterceptor;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** Internal services authenticate independently of user JWTs. */
@Configuration
public class InternalApiSecurity implements WebMvcConfigurer, HandlerInterceptor {
    public static final String HEADER = "X-OJ-Internal-Key";
    private final String key;

    public InternalApiSecurity(@Value("${INTERNAL_API_KEY}") String key) {
        if (key == null || key.length() < 32) throw new IllegalArgumentException("INTERNAL_API_KEY must contain at least 32 characters");
        this.key = key;
    }

    @Bean
    public RequestInterceptor internalRequestInterceptor() {
        return template -> {
            template.header(HEADER, key);
            var request = AuditContext.currentRequest();
            if (request != null && request.getAttribute(AuditContext.REQUEST_ID) instanceof String requestId) {
                template.header("X-Request-ID", requestId);
            }
        };
    }

    @Bean
    public RequestAuditFilter requestAuditFilter(@Value("${jwt.secret}") String secret,
            @Value("${spring.application.name:unknown}") String service) {
        return new RequestAuditFilter(secret, key, service);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(this).addPathPatterns("/**").order(-100);
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String path = request.getRequestURI();
        if (path.contains("%") || path.contains(";") || path.contains("\\") || path.contains("//")
                || path.matches(".*/\\.\\.?(?:/.*)?")) {
            response.setStatus(400);
            return false;
        }
        boolean internal = path.matches(".*/(?:inner|internal)(?:/.*)?") || path.startsWith("/judge/");
        if (!internal) return true;
        String supplied = request.getHeader(HEADER);
        if (supplied != null && MessageDigest.isEqual(key.getBytes(StandardCharsets.UTF_8), supplied.getBytes(StandardCharsets.UTF_8))) return true;
        response.setStatus(403);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":403,\"message\":\"禁止访问内部接口\"}");
        return false;
    }

}
