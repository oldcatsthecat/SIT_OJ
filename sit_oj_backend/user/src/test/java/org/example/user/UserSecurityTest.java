package org.example.user;
import org.example.user.config.JwtProperties;
import org.example.user.entity.User;
import org.example.user.utils.JwtUtils;
import org.example.user.service.VerificationCodeService;
import org.example.user.service.impl.UserServiceImpl;
import org.example.user.mapper.UserMapper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.test.util.ReflectionTestUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import java.util.*;
class UserSecurityTest {
 JwtUtils jwt(String secret){var p=new JwtProperties();p.setSecret(secret);p.setExpiration(86400000);var j=new JwtUtils();ReflectionTestUtils.setField(j,"jwtProperties",p);return j;}
 @Test void rotatedKeyRejectsPreviouslyValidAdminAndRefreshTokens(){
  User admin=new User();admin.setId(1);admin.setUsername("admin");admin.setRole("ADMIN");
  String oldToken=jwt(UUID.randomUUID().toString()).generateToken(admin);
  var current=jwt(UUID.randomUUID().toString());
  assertThrows(Exception.class,()->current.getUserIdFromToken(oldToken));
  assertThrows(Exception.class,()->current.getClaimsAllowExpired(oldToken));
  assertEquals(1,current.getUserIdFromToken(current.generateToken(admin)));
 }
 @Test void serializedUserCannotLeakPasswordsOrVerificationCodes() throws Exception {
  User u=new User();u.setPassword("private-password");u.setEmailCode("123456");u.setCodeExpireTime(java.time.LocalDateTime.now());
  String json=new ObjectMapper().writeValueAsString(u);
  assertFalse(json.contains("private-password"));assertFalse(json.contains("123456"));assertFalse(json.contains("codeExpireTime"));
 }
 @Test void rejectsInvalidMailAndPurposeWithoutUsingRedis(){
  var redis=mock(StringRedisTemplate.class);var service=new VerificationCodeService(redis);
  for(String email:Arrays.asList("a@b.c","invalid","a@b.com\r\nBcc: victim@example.com",null))assertThrows(IllegalArgumentException.class,()->service.issue(email,"register"));
  assertThrows(IllegalArgumentException.class,()->service.issue("user@example.com","arbitrary"));verifyNoInteractions(redis);
 }
 @Test void normalizesEmailConsistently(){assertEquals("test@example.com",new VerificationCodeService(mock(StringRedisTemplate.class)).normalizeEmail(" Test@Example.com "));}
 @Test void failsClosedIfVerificationStoreUnavailable(){
  var redis=mock(StringRedisTemplate.class);var service=new VerificationCodeService(redis);
  assertThrows(IllegalArgumentException.class,()->service.consume("user@example.com","register","123456"));
  assertThrows(IllegalArgumentException.class,()->service.issue("user@example.com","register"));
 }
 @Test void registeredAccountCannotBeOverwrittenThroughRegister(){
  var service=new UserServiceImpl();var mapper=mock(UserMapper.class);var codes=mock(VerificationCodeService.class);
  ReflectionTestUtils.setField(service,"baseMapper",mapper);ReflectionTestUtils.setField(service,"verificationCodes",codes);
  when(codes.normalizeEmail("user@example.com")).thenReturn("user@example.com");
  var existing=new User();existing.setPassword("existing-hash");existing.setLocked(true);when(mapper.selectOne(any(),anyBoolean())).thenReturn(existing);
  assertThrows(IllegalArgumentException.class,()->service.register(Map.of("email","user@example.com","username","attacker","password","somepassword","code","123456")));
  verify(codes,never()).consume(anyString(),anyString(),any());verify(mapper,never()).update(any(),any());
 }
 @Test void lockedAccountCannotLogInWithCorrectPassword(){
  var service=new UserServiceImpl();var mapper=mock(UserMapper.class);
  var encoder=mock(PasswordEncoder.class);var jwt=mock(JwtUtils.class);
  ReflectionTestUtils.setField(service,"baseMapper",mapper);
  ReflectionTestUtils.setField(service,"passwordEncoder",encoder);
  ReflectionTestUtils.setField(service,"jwtUtils",jwt);
  var existing=new User();existing.setPassword("hash");existing.setLocked(true);
  when(mapper.selectOne(any(),anyBoolean())).thenReturn(existing);
  when(encoder.matches("correct-password","hash")).thenReturn(true);
  assertEquals("账户已禁用",assertThrows(IllegalArgumentException.class,
   ()->service.login("user@example.com","correct-password")).getMessage());
  verify(jwt,never()).generateToken(any());
 }
 @Test void mailDisabledStopsBeforeDatabaseOrSend(){
  var service=new UserServiceImpl();assertThrows(IllegalArgumentException.class,()->service.sendCode("test@example.com","register"));
 }
}
