package org.example.common.security;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import static org.junit.jupiter.api.Assertions.*;
class InternalApiSecurityTest {
 private final String key = java.util.UUID.randomUUID().toString();
 private final InternalApiSecurity security = new InternalApiSecurity(key);
 @Test void rejectsMissingKeyOnEveryInternalRoute() throws Exception {
  for(String path : new String[]{"/users/inner/1","/problems/inner/22","/submissions/inner/submit","/competitions/internal/updateStats","/judge/doJudge"}){
   var req=new MockHttpServletRequest("POST",path); var res=new MockHttpServletResponse();
   assertFalse(security.preHandle(req,res,new Object()),path); assertEquals(403,res.getStatus());
  }
 }
 @Test void acceptsCorrectKeyAndRejectsWrongKey() throws Exception {
  var req=new MockHttpServletRequest("GET","/problems/inner/22");
  req.addHeader(InternalApiSecurity.HEADER,"incorrect");
  assertFalse(security.preHandle(req,new MockHttpServletResponse(),new Object()));
  req.removeHeader(InternalApiSecurity.HEADER);req.addHeader(InternalApiSecurity.HEADER,key);
  assertTrue(security.preHandle(req,new MockHttpServletResponse(),new Object()));
 }
 @Test void leavesPublicRankOpen() throws Exception {
  assertTrue(security.preHandle(new MockHttpServletRequest("GET","/competitions/1/rank"),new MockHttpServletResponse(),new Object()));
 }
 @Test void rejectsAmbiguousPathsBeforeAuthentication() throws Exception {
  for(String path : new String[]{"/problems/%69nner/1","/problems/inner;x/1","//judge/doJudge","/x/../judge/doJudge","/problems\\inner\\1"}) {
   var req=new MockHttpServletRequest("GET",path); var res=new MockHttpServletResponse();
   assertFalse(security.preHandle(req,res,new Object()),path); assertEquals(400,res.getStatus());
  }
 }
 @Test void refusesShortConfigurationKey(){assertThrows(IllegalArgumentException.class,()->new InternalApiSecurity("short"));}
 @Test void rejectsUnexpectedLanguagesAndOversizedSource(){
  for(String value:new String[]{"bash","rust","deepseek","你好世界","cpp;sh"}) assertThrows(RuntimeException.class,()->SubmissionInput.language(value));
  assertEquals("python",SubmissionInput.language("py"));
  assertThrows(RuntimeException.class,()->SubmissionInput.validate(1,"x".repeat(262145)));
 }
}
