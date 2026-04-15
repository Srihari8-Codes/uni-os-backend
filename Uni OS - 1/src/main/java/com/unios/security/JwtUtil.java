package com.unios.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.util.Date;

@Component
public class JwtUtil {

    private final String SECRET_KEY = "super_secret_key_which_should_be_very_long_for_security_purposes";
    private final Key key = Keys.hmacShaKeyFor(SECRET_KEY.getBytes());

    public String generateToken(com.unios.model.User user) {
        return Jwts.builder()
                .setSubject(user.getEmail())
                .claim("userId", user.getId())
                .claim("universityId", user.getUniversity() != null ? user.getUniversity().getId() : null)
                .claim("role", user.getRole().name())
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + 1000 * 60 * 60 * 10)) // 10 hours
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    public String extractUsername(String token) {
        return extractClaims(token).getSubject();
    }

    public Long extractUserId(String token) {
        return extractLongClaim(token, "userId");
    }

    public Long extractUniversityId(String token) {
        return extractLongClaim(token, "universityId");
    }

    public String extractRole(String token) {
        return (String) extractClaims(token).get("role");
    }

    private Claims extractClaims(String token) {
        return Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(token).getBody();
    }

    private Long extractLongClaim(String token, String claimName) {
        Object value = extractClaims(token).get(claimName);
        if (value instanceof Integer) return ((Integer) value).longValue();
        if (value instanceof Long) return (Long) value;
        if (value instanceof String) return Long.parseLong((String) value);
        return null;
    }

    public boolean validateToken(String token, String email) {
        return (extractUsername(token).equals(email) && !isTokenExpired(token));
    }

    private boolean isTokenExpired(String token) {
        return Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(token).getBody().getExpiration()
                .before(new Date());
    }
}
