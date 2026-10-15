package org.example.user;
import org.example.user.config.WebMvcConfig;
import org.example.user.interceptor.AdminInterceptor;
import org.example.user.interceptor.LoginInterceptor;
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
  for(String path:new String[]{"/admin/users/list","/admin/users/update","/admin/users/1","/admin/users/1/locked"}){
   var request=new MockHttpServletRequest("POST",path);ServletRequestPathUtils.parseAndCache(request);
   assertTrue(interceptors.stream().anyMatch(i->i instanceof MappedInterceptor mapped && mapped.getInterceptor()==admin && mapped.matches(request)),path);
   var response=new MockHttpServletResponse();assertFalse(admin.preHandle(request,response,new Object()));assertEquals(403,response.getStatus());
  }
 }
}
