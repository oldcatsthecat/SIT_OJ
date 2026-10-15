package org.example.gateway;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import reactor.core.publisher.Mono;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class GatewaySecurityTest {
 final InternalApiFilter filter=new InternalApiFilter();
 @Test void blocksInternalJudgeAndAmbiguousPaths(){
  for(String path:new String[]{"/problems/inner/22","/submissions/inner/submit","/competitions/internal/updateStats","/judge/doJudge","/admin/judge/judge_server_heartbeat","/problems/inner","/users/%69nner/1","/users/inner;x/1","/users//inner/1"}){
   var exchange=MockServerWebExchange.from(MockServerHttpRequest.get(path).build());var chain=mock(GatewayFilterChain.class);
   filter.filter(exchange,chain).block();assertEquals(403,exchange.getResponse().getStatusCode().value(),path);verifyNoInteractions(chain);
  }
 }
 @Test void stripsSpoofedServiceCredentialsButAllowsPublicScoreboard(){
  var exchange=MockServerWebExchange.from(MockServerHttpRequest.get("/competitions/1/rank").header("X-OJ-Internal-Key","fake").build());
  filter.filter(exchange,e->{assertNull(e.getRequest().getHeaders().getFirst("X-OJ-Internal-Key"));return Mono.empty();}).block();
 }
}
