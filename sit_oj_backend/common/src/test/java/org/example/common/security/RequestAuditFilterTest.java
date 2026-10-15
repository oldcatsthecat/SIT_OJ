package org.example.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.example.common.utils.Result;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class RequestAuditFilterTest {
    final String secret = UUID.randomUUID().toString() + UUID.randomUUID();
    final String internalKey = UUID.randomUUID().toString();
    final List<Map<String,Object>> records = new ArrayList<>();
    final RequestAuditFilter filter = new RequestAuditFilter(secret, internalKey, "submission", records::add);
    String token(String signingKey, long expiration) {
        return Jwts.builder().claim("id", 77).claim("role", "USER").setSubject("account-A")
                .setExpiration(new Date(expiration)).signWith(Keys.hmacShaKeyFor(signingKey.getBytes(StandardCharsets.UTF_8)), SignatureAlgorithm.HS256).compact();
    }
    String validToken() { return token(secret, System.currentTimeMillis() + 60_000); }
    @Test void recordsVerifiedAccountAndSpoofAttemptWithoutSecretsOrCode() throws Exception {
        String auth = validToken();
        var req = new MockHttpServletRequest("POST", "/submissions/submit");
        req.addHeader("Authorization", "Bearer " + auth);
        req.addHeader("X-Request-ID", "trace-123");
        req.addHeader("User-Agent", "agent\nforged-line");
        req.setQueryString("password=never-log-query");
        req.setContent("password=never-log-body&code=never-log-code".getBytes(StandardCharsets.UTF_8));
        req.setAttribute("userId", 12345); // Even a forged request attribute must not override verified claims.
        var res = new MockHttpServletResponse();
        filter.doFilter(req, res, (request,response) -> {
            AuditContext.submissionAttempt(req, 88, 22, null, "py");
            AuditContext.submissionSaved(req, 551, 77);
        });
        var event=records.get(0);
        assertEquals(77,event.get("actorUserId")); assertEquals("account-A",event.get("actorUsername"));
        assertEquals(88L,event.get("claimedUserId")); assertEquals(true,event.get("userIdMismatch"));
        assertEquals(551L,event.get("submissionId")); assertEquals(77L,event.get("submittedUserId"));
        assertEquals(AuditIdentity.fingerprint(auth),event.get("tokenFingerprint"));
        assertEquals("trace-123",res.getHeader("X-Request-ID"));
        String json=new ObjectMapper().writeValueAsString(event);
        assertFalse(json.contains(auth)); assertFalse(json.contains("never-log")); assertFalse(json.contains("\n"));
        assertEquals(77,new ObjectMapper().readTree(json).get("actorUserId").intValue());
    }
    @Test void forgedAndExpiredTokensNeverIdentifyAccounts() throws Exception {
        for(String auth : List.of(token(UUID.randomUUID().toString()+UUID.randomUUID(),System.currentTimeMillis()+60_000),
                token(secret,System.currentTimeMillis()-60_000),"not-a-jwt")) {
            var req=new MockHttpServletRequest("POST","/submissions/submit");req.addHeader("Authorization",auth);
            filter.doFilter(req,new MockHttpServletResponse(),(request,response)->{});
            assertNull(records.get(records.size()-1).get("actorUserId"));
            assertNotEquals("VALID",records.get(records.size()-1).get("authStatus"));
        }
    }
    @Test void logsInternalAuthenticationDenialEvenWhenInterceptorReturnsFalse() throws Exception {
        var req=new MockHttpServletRequest("GET","/problems/inner/22"); var res=new MockHttpServletResponse();
        var security=new InternalApiSecurity(internalKey);
        filter.doFilter(req,res,(request,response)-> {
            try { assertFalse(security.preHandle(req,res,new Object())); } catch(Exception error) { throw new ServletExceptionForTest(error); }
        });
        assertEquals(403,records.get(0).get("httpStatus"));assertEquals("MISSING",records.get(0).get("authStatus"));
        assertEquals(false,records.get(0).get("internalAuthenticated"));
    }
    @Test void exceptionsAreRecordedWithoutErrorMessageContents() {
        var req=new MockHttpServletRequest("POST","/submissions/submit");
        assertThrows(IOException.class,()->filter.doFilter(req,new MockHttpServletResponse(),(r,s)->{throw new IOException("private detail");}));
        assertEquals(500,records.get(0).get("httpStatus")); assertEquals("IOException",records.get(0).get("exceptionType"));
        assertFalse(records.get(0).toString().contains("private detail"));
    }
    @Test void loginRecordsIssuedAccountAndFingerprintButNotIssuedToken() throws Exception {
        var req=new MockHttpServletRequest("POST","/users/login");var res=new MockHttpServletResponse();String auth=validToken();
        filter.doFilter(req,res,(r,s)->new AuditResultAdvice().beforeBodyWrite(Result.success(auth),null,null,null,
                new ServletServerHttpRequest(req),new ServletServerHttpResponse(res)));
        assertEquals(77,records.get(0).get("issuedUserId")); assertEquals(AuditIdentity.fingerprint(auth),records.get(0).get("issuedTokenFingerprint"));
        assertFalse(records.get(0).toString().contains(auth));
    }
    @Test void distinguishesBusinessFailureFromHttpSuccess() throws Exception {
        var req=new MockHttpServletRequest("POST","/competitions/20/submit");var res=new MockHttpServletResponse();
        filter.doFilter(req,res,(r,s)->new AuditResultAdvice().beforeBodyWrite(Result.error("not registered"),null,null,null,
                new ServletServerHttpRequest(req),new ServletServerHttpResponse(res)));
        assertEquals(200,records.get(0).get("httpStatus")); assertEquals(500,records.get(0).get("businessCode"));
    }
    @Test void propagatesTraceThroughInternalFeignCalls() throws Exception {
        var req=new MockHttpServletRequest("POST","/competitions/20/submit");req.addHeader("X-Request-ID","trace-456");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(req));
        try {
            filter.doFilter(req,new MockHttpServletResponse(),(r,s)-> {
                var template=new feign.RequestTemplate();new InternalApiSecurity(internalKey).internalRequestInterceptor().apply(template);
                assertEquals(List.of("trace-456"),new ArrayList<>(template.headers().get("X-Request-ID")));
            });
        } finally { RequestContextHolder.resetRequestAttributes(); }
    }
    @Test void scannedConfigurationRegistersOneFilterAndOneAdvice() {
        try(var context=new AnnotationConfigApplicationContext()) {
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test",Map.of("jwt.secret",secret,"INTERNAL_API_KEY",internalKey)));
            context.scan("org.example.common.security");context.refresh();
            assertEquals(1,context.getBeansOfType(RequestAuditFilter.class).size());
            assertEquals(1,context.getBeansOfType(AuditResultAdvice.class).size());
        }
    }
    static class ServletExceptionForTest extends jakarta.servlet.ServletException { ServletExceptionForTest(Throwable e){super(e);} }
}
