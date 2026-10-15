package org.example.gateway;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 拦截外部对内部接口（Feign 调用专用）的请求
 * 路径含 /inner/ 或 /internal/ 的接口仅供微服务间调用，不对外开放
 */
@Component
public class InternalApiFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(InternalApiFilter.class);

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getRawPath();
        if (path.contains("%") || path.contains(";") || path.contains("\\") || path.contains("//")
                || path.matches(".*/\\.\\.?(?:/.*)?")
                || path.matches(".*/(?:inner|internal)(?:/.*)?")
                || path.startsWith("/judge/") || path.equals("/judge")
                || path.equals("/admin/judge/judge_server_heartbeat")) {
            log.warn("拦截非法外部请求: {}", path);
            exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
            return exchange.getResponse().setComplete();
        }
        return chain.filter(exchange.mutate().request(exchange.getRequest().mutate()
                .headers(headers -> headers.remove("X-OJ-Internal-Key")).build()).build());
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
