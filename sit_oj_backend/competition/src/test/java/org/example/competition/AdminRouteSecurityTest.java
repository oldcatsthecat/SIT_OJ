package org.example.competition;
import org.example.competition.config.WebMvcConfig;
import org.example.competition.interceptor.AdminInterceptor;
import org.example.competition.interceptor.LoginInterceptor;
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

  ReflectionTestUtils.setField(config,"adminInterceptor",admin);
  ReflectionTestUtils.setField(config,"loginInterceptor",mock(LoginInterceptor.class));
  var registry=new InterceptorRegistry();config.addInterceptors(registry);
  List<?> interceptors=ReflectionTestUtils.invokeMethod(registry,"getInterceptors");
  for(String path:new String[]{"/admin/competitions/create","/admin/competitions/20/export","/admin/competitions/delete/1"}){
   var request=new MockHttpServletRequest("POST",path);ServletRequestPathUtils.parseAndCache(request);
   assertTrue(interceptors.stream().anyMatch(i->i instanceof MappedInterceptor mapped && mapped.getInterceptor()==admin && mapped.matches(request)),path);
   var response=new MockHttpServletResponse();assertFalse(admin.preHandle(request,response,new Object()));assertEquals(403,response.getStatus());
  }
 }
}
