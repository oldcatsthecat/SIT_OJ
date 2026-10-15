package org.example.judge;
import org.example.judge.config.WebMvcConfig;
import org.example.judge.interceptor.AdminInterceptor;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.handler.MappedInterceptor;
import org.springframework.web.util.ServletRequestPathUtils;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class AdminRouteSecurityTest {
 @Test void actualAdminRoutesAreProtectedAfterProxyPrefixRemoval() throws Exception {
  var config=new WebMvcConfig();var admin=new AdminInterceptor();
  ReflectionTestUtils.setField(admin,"jwtUtils",mock(org.example.judge.utils.JwtUtils.class));
  ReflectionTestUtils.setField(config,"adminInterceptor",admin);

  var registry=new InterceptorRegistry();config.addInterceptors(registry);
  List<?> interceptors=ReflectionTestUtils.invokeMethod(registry,"getInterceptors");
  for(String path:new String[]{"/admin/judge/server_status"}){
   var request=new MockHttpServletRequest("POST",path);ServletRequestPathUtils.parseAndCache(request);
   assertTrue(interceptors.stream().anyMatch(i->i instanceof MappedInterceptor mapped && mapped.getInterceptor()==admin && mapped.matches(request)),path);
   var response=new MockHttpServletResponse();assertFalse(admin.preHandle(request,response,new Object()));assertEquals(403,response.getStatus());
  }
 }
}
