package org.example.gateway;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class RequestAuditWebFilterTest {
    @Test void logsRejectedInternalRequestAndOnlyFingerprintsAuth() {
        List<Map<String,Object>> records=new ArrayList<>();var filter=new RequestAuditWebFilter(records::add);
        var exchange=MockServerWebExchange.from(MockServerHttpRequest.get("/problems/inner/22?password=private")
                .header("Authorization","Bearer sensitive-token").header("X-Request-ID","req-123"));
        filter.filter(exchange,e->new InternalApiFilter().filter(e,unused->Mono.empty())).block();
        assertEquals(1,records.size());assertEquals(403,records.get(0).get("httpStatus"));
        assertEquals("NOT_VERIFIED",records.get(0).get("authStatus"));assertFalse(records.get(0).containsKey("actorUserId"));
        assertFalse(records.get(0).toString().contains("sensitive-token"));assertFalse(records.get(0).toString().contains("private"));
    }
    @Test void forwardsOneTraceAndLogsUnmatchedRequests() {
        List<Map<String,Object>> records=new ArrayList<>();var filter=new RequestAuditWebFilter(records::add);
        var exchange=MockServerWebExchange.from(MockServerHttpRequest.get("/unknown"));
        filter.filter(exchange,e->{
            assertEquals(exchange.getResponse().getHeaders().getFirst("X-Request-ID"),e.getRequest().getHeaders().getFirst("X-Request-ID"));
            e.getResponse().setStatusCode(HttpStatus.NOT_FOUND);return e.getResponse().setComplete();
        }).block();
        assertEquals(404,records.get(0).get("httpStatus"));assertEquals("MISSING",records.get(0).get("authStatus"));
    }
}
