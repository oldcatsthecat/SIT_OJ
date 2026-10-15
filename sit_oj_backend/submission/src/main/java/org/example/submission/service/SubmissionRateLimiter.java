package org.example.submission.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;

@Service
public class SubmissionRateLimiter {
    private final StringRedisTemplate redis;
    private static final DefaultRedisScript<Long> LIMIT = new DefaultRedisScript<>(
            "local n=redis.call('INCR',KEYS[1]); if n==1 then redis.call('EXPIRE',KEYS[1],60) end; return n", Long.class);
    public SubmissionRateLimiter(StringRedisTemplate redis) { this.redis = redis; }
    public void check(Integer userId) {
        Long count = redis.execute(LIMIT, List.of("submission:rate:" + userId));
        if (count == null || count > 20) throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "提交过于频繁，请稍后重试");
    }
}
