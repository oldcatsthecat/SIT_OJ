package org.example.common.security;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.logging.LogFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public final class RequestAuditFilter extends OncePerRequestFilter {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final AuditIdentity verifier;
    private final String internalKey;
    private final String service;
    private final Consumer<Map<String, Object>> writer;

    public RequestAuditFilter(String secret, String internalKey, String service) {
        this(secret, internalKey, service, event -> {
            try { LogFactory.getLog("OJAccessAudit").info(JSON.writeValueAsString(event)); }
            catch (JsonProcessingException error) { throw new IllegalStateException("Cannot encode audit record", error); }
        });
    }
    public RequestAuditFilter(String secret, String internalKey, String service, Consumer<Map<String, Object>> writer) {
        this.verifier = new AuditIdentity(secret);
        this.internalKey = internalKey;
        this.service = service;
        this.writer = writer;
    }
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long started = System.nanoTime();
        String requestId = request.getHeader("X-Request-ID");
        if (requestId == null || !requestId.matches("[A-Za-z0-9-]{1,64}")) requestId = UUID.randomUUID().toString();
        request.setAttribute(AuditContext.REQUEST_ID, requestId);
        request.setAttribute(AuditContext.IDENTITY_VERIFIER, verifier);
        response.setHeader("X-Request-ID", requestId);
        var identity = verifier.inspect(request.getHeader("Authorization"));
        Throwable failure = null;
        try { chain.doFilter(request, response); }
        catch (IOException | ServletException | RuntimeException error) { failure = error; throw error; }
        finally {
            Map<String, Object> event = new LinkedHashMap<>();
            event.put("timestamp", Instant.now().toString());
            event.put("service", service);
            event.put("requestId", requestId);
            event.put("method", request.getMethod());
            event.put("path", AuditIdentity.bounded(request.getRequestURI(), 2048));
            event.put("peerIp", request.getRemoteAddr());
            String ip = request.getHeader("X-Real-IP");
            event.put("clientIp", ip != null && ip.matches("[0-9a-fA-F:.]{1,64}") ? ip : request.getRemoteAddr());
            event.put("userAgent", AuditIdentity.bounded(request.getHeader("User-Agent"), 512));
            identity.addTo(event);
            String supplied = request.getHeader(InternalApiSecurity.HEADER);
            event.put("internalAuthenticated", supplied != null && MessageDigest.isEqual(
                    internalKey.getBytes(StandardCharsets.UTF_8), supplied.getBytes(StandardCharsets.UTF_8)));
            event.putAll(AuditContext.details(request));
            Long claimed = AuditContext.numericId(event.get("claimedUserId"));
            event.put("userIdMismatch", claimed != null && identity.userId() != null
                    ? claimed.longValue() != identity.userId().longValue() : null);
            event.put("httpStatus", failure == null ? response.getStatus() : 500);
            event.put("durationMs", (System.nanoTime() - started) / 1_000_000);
            if (failure != null) event.put("exceptionType", failure.getClass().getSimpleName());
            writer.accept(event);
        }
    }
}
