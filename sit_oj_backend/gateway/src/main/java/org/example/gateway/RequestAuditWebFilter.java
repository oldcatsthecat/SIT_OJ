package org.example.gateway;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.core.publisher.SignalType;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/** Also logs unmatched routes and requests rejected before any business service is called. */
@Component
public class RequestAuditWebFilter implements WebFilter, Ordered {
    private final Consumer<Map<String, Object>> writer;
    @Autowired public RequestAuditWebFilter(ObjectMapper mapper) {
        this(event -> {
            try { LoggerFactory.getLogger("OJAccessAudit").info(mapper.writeValueAsString(event)); }
            catch (JsonProcessingException error) { throw new IllegalStateException("Cannot encode audit record", error); }
        });
    }
    public RequestAuditWebFilter(Consumer<Map<String, Object>> writer) { this.writer = writer; }
    @Override public int getOrder() { return Ordered.HIGHEST_PRECEDENCE; }
    @Override public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String suppliedId = exchange.getRequest().getHeaders().getFirst("X-Request-ID");
        String requestId = suppliedId != null && suppliedId.matches("[A-Za-z0-9-]{1,64}")
                ? suppliedId : UUID.randomUUID().toString();
        var forwarded = exchange.mutate().request(exchange.getRequest().mutate()
                .headers(headers -> headers.set("X-Request-ID", requestId)).build()).build();
        exchange.getResponse().getHeaders().set("X-Request-ID", requestId);
        long started = System.nanoTime();
        AtomicInteger failureStatus = new AtomicInteger();
        return Mono.defer(() -> chain.filter(forwarded)).doOnError(error -> failureStatus.set(
                error instanceof ResponseStatusException status ? status.getStatusCode().value() : 500))
                .doFinally(signal -> {
                    var request = exchange.getRequest();
                    Map<String, Object> event = new LinkedHashMap<>();
                    event.put("timestamp", Instant.now().toString());
                    event.put("service", "gateway");
                    event.put("requestId", requestId);
                    event.put("method", request.getMethod().name());
                    event.put("path", bounded(request.getURI().getRawPath(), 2048));
                    String peer = request.getRemoteAddress() == null ? null : request.getRemoteAddress().getAddress().getHostAddress();
                    String ip = request.getHeaders().getFirst("X-Real-IP");
                    event.put("peerIp", peer);
                    event.put("clientIp", ip != null && ip.matches("[0-9a-fA-F:.]{1,64}") ? ip : peer);
                    event.put("userAgent", bounded(request.getHeaders().getFirst("User-Agent"), 512));
                    String authorization = request.getHeaders().getFirst("Authorization");
                    String token = authorization == null ? "" : authorization.trim();
                    if (token.startsWith("Bearer ")) token = token.substring(7).trim();
                    event.put("tokenFingerprint", token.isEmpty() ? null : fingerprint(token));
                    // Account attribution only happens in the signature-verifying business service.
                    event.put("authStatus", token.isEmpty() ? "MISSING" : "NOT_VERIFIED");
                    HttpStatusCode status = exchange.getResponse().getStatusCode();
                    event.put("httpStatus", signal == SignalType.CANCEL ? 499 : failureStatus.get() != 0
                            ? failureStatus.get() : status == null ? 200 : status.value());
                    event.put("durationMs", (System.nanoTime() - started) / 1_000_000);
                    writer.accept(event);
                });
    }
    private static String bounded(String value, int length) { return value == null ? null : value.substring(0, Math.min(value.length(), length)); }
    private static String fingerprint(String token) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
}
