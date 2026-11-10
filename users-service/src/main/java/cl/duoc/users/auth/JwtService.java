package cl.duoc.users.auth;

import cl.duoc.users.user.AppUser;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class JwtService {
    private final SecretKey key;
    private final Duration expiration;

    public JwtService(@Value("${app.jwt.secret}") String encodedSecret,
            @Value("${app.jwt.expiration:PT1H}") Duration expiration) {
        byte[] secret;
        try {
            secret = Base64.getDecoder().decode(encodedSecret);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("app.jwt.secret must be a Base64-encoded 256-bit key", ex);
        }
        if (secret.length < 32) {
            throw new IllegalArgumentException("app.jwt.secret must decode to at least 32 bytes");
        }
        if (expiration.isNegative() || expiration.isZero()) {
            throw new IllegalArgumentException("app.jwt.expiration must be positive");
        }
        this.key = Keys.hmacShaKeyFor(secret);
        this.expiration = expiration;
    }

    public String issue(AppUser user) {
        Instant now = Instant.now();
        return Jwts.builder().subject(user.getUsername()).issuedAt(java.util.Date.from(now))
                .expiration(java.util.Date.from(now.plus(expiration))).signWith(key).compact();
    }

    public String subject(String token) {
        Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
        return claims.getSubject();
    }
}
