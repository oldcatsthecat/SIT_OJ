package org.example.user.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

/** Challenges are purpose-bound, expire after 5 minutes and allow at most 5 guesses. */
@Service
public class VerificationCodeService {
    private final StringRedisTemplate redis;
    private final SecureRandom random = new SecureRandom();
    private static final DefaultRedisScript<Long> ISSUE = new DefaultRedisScript<>("""
        if redis.call('EXISTS', KEYS[1]) == 1 then return 0 end
        local count = redis.call('INCR', KEYS[2])
        if count == 1 then redis.call('EXPIRE', KEYS[2], 3600) end
        if count > 5 then return 0 end
        local total = redis.call('INCR', KEYS[4])
        if total == 1 then redis.call('EXPIRE', KEYS[4], 3600) end
        if total > 200 then return 0 end
        redis.call('SET', KEYS[1], '1', 'EX', 60)
        redis.call('HSET', KEYS[3], 'hash', ARGV[1], 'remaining', 5)
        redis.call('EXPIRE', KEYS[3], 300)
        return 1
        """, Long.class);
    private static final DefaultRedisScript<Long> VERIFY = new DefaultRedisScript<>("""
        local expected = redis.call('HGET', KEYS[1], 'hash')
        if not expected then return 0 end
        if expected == ARGV[1] then redis.call('DEL', KEYS[1]); return 1 end
        local remaining = redis.call('HINCRBY', KEYS[1], 'remaining', -1)
        if remaining <= 0 then redis.call('DEL', KEYS[1]) end
        return 0
        """, Long.class);

    public VerificationCodeService(StringRedisTemplate redis) { this.redis = redis; }

    public String normalizeEmail(String email) {
        if (email == null) throw new IllegalArgumentException("邮箱地址无效");
        email = email.trim().toLowerCase(Locale.ROOT);
        if (email.length() > 254 || !email.matches("[a-z0-9.!#$%&'*+/=?^_`{|}~-]{1,64}@[a-z0-9](?:[a-z0-9-]*[a-z0-9])?(?:\\.[a-z0-9](?:[a-z0-9-]*[a-z0-9])?)*\\.[a-z]{2,63}")) {
            throw new IllegalArgumentException("邮箱地址无效");
        }
        return email;
    }

    public String issue(String email, String purpose) {
        email = normalizeEmail(email);
        checkPurpose(purpose);
        String code = String.format("%06d", random.nextInt(1_000_000));
        String id = hash(email);
        Long allowed = redis.execute(ISSUE, List.of("otp:cooldown:" + id, "otp:hour:" + id,
                key(email, purpose), "otp:global:hour"), hash(code));
        if (!Long.valueOf(1).equals(allowed)) throw new IllegalArgumentException("验证码发送过于频繁，请稍后再试");
        return code;
    }

    public void consume(String email, String purpose, String code) {
        email = normalizeEmail(email);
        checkPurpose(purpose);
        // Invalid shapes also consume an attempt; Redis failure must never allow access.
        Long accepted = redis.execute(VERIFY, List.of(key(email, purpose)), hash(code == null ? "" : code));
        if (!Long.valueOf(1).equals(accepted)) throw new IllegalArgumentException("验证码错误、已过期或尝试次数过多，请重新获取");
    }

    private void checkPurpose(String purpose) {
        if (!"register".equals(purpose) && !"update".equals(purpose)) throw new IllegalArgumentException("验证码用途无效");
    }
    private String key(String email, String purpose) { return "otp:challenge:" + purpose + ":" + hash(email); }
    private String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
}
