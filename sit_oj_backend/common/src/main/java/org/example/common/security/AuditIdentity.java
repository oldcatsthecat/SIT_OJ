package org.example.common.security;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;

/** Only a signature-verified, unexpired token can identify an account in audit records. */
public final class AuditIdentity {
    private final JwtParser parser;
    public AuditIdentity(String secret) {
        parser = Jwts.parserBuilder().setSigningKey(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8))).build();
    }
    public record Identity(String status, String fingerprint, Integer userId, String username, String role) {
        public void addTo(Map<String, Object> event) {
            event.put("authStatus", status);
            event.put("tokenFingerprint", fingerprint);
            event.put("actorUserId", userId);
            event.put("actorUsername", username);
            event.put("actorRole", role);
        }
    }
    public Identity inspect(String authorization) {
        String token = token(authorization);
        if (token.isEmpty()) return new Identity("MISSING", null, null, null, null);
        String fingerprint = fingerprint(token);
        if (token.length() > 16384) return new Identity("INVALID", fingerprint, null, null, null);
        try {
            var claims = parser.parseClaimsJws(token).getBody();
            Integer id = Integer.valueOf(String.valueOf(claims.get("id")));
            if (id <= 0 || claims.getExpiration() == null) throw new IllegalArgumentException();
            return new Identity("VALID", fingerprint, id, bounded(claims.getSubject(), 128),
                    bounded(claims.get("role", String.class), 32));
        } catch (ExpiredJwtException expired) {
            return new Identity("EXPIRED", fingerprint, null, null, null);
        } catch (RuntimeException invalid) {
            return new Identity("INVALID", fingerprint, null, null, null);
        }
    }
    public static String token(String authorization) {
        if (authorization == null) return "";
        String value = authorization.trim();
        return value.startsWith("Bearer ") ? value.substring(7).trim() : value;
    }
    public static String fingerprint(String token) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    public static String bounded(String value, int length) {
        return value == null ? null : value.substring(0, Math.min(value.length(), length));
    }
}
