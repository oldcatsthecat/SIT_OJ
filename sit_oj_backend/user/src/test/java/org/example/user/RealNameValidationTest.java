package org.example.user;

import org.example.user.entity.User;
import org.example.user.mapper.UserMapper;
import org.example.user.service.impl.UserServiceImpl;
import org.example.user.utils.RealNameValidator;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class RealNameValidationTest {
    @Test void acceptsAnyNonblankTextUpToFiftyCharacters() {
        assertTrue(RealNameValidator.isValid("O'Connor; DROP TABLE users; --"));
        assertTrue(RealNameValidator.isValid("😀".repeat(50)));
        assertFalse(RealNameValidator.isValid("😀".repeat(51)));
        assertFalse(RealNameValidator.isValid(" "));
        assertFalse(RealNameValidator.isValid(null));
    }

    @Test void profileUpdateRejectsOverlongNameBeforeDatabaseWrite() {
        var service = new UserServiceImpl();
        var mapper = mock(UserMapper.class);
        ReflectionTestUtils.setField(service, "baseMapper", mapper);
        var current = new User();
        current.setId(1);
        when(mapper.selectById(1)).thenReturn(current);
        var submitted = new User();
        submitted.setId(1);
        submitted.setRealName("x".repeat(51));

        assertEquals("姓名不能为空且不能超过50个字符", assertThrows(IllegalArgumentException.class,
                () -> service.updateUserInfo(submitted, null)).getMessage());
        verify(mapper, never()).updateById(any(User.class));
    }
}
