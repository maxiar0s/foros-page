package cl.duoc.forum;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.filter.OncePerRequestFilter;

public record Identity(Long id, String username, String role) {
}

@Service
class IdentityClient {
    private final RestTemplate http;

    @Value("${users-service.url:http://users-service:8081}")
    private String base;

    public IdentityClient(RestTemplate http) {
        this.http = http;
    }

    public Identity authenticate(String token) {
        try {
            return http.exchange(base + "/api/auth/me", HttpMethod.GET,
                    new HttpEntity<>(authorization(token)), Identity.class).getBody();
        } catch (RestClientException exception) {
            throw new BadCredentialsException("Identity validation failed", exception);
        }
    }

    private HttpHeaders authorization(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }
}

class BearerFilter extends OncePerRequestFilter {
    private final IdentityClient client;

    public BearerFilter(IdentityClient client) {
        this.client = client;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String authorization = request.getHeader("Authorization");
        if (authorization == null || !authorization.startsWith("Bearer ") || authorization.length() == 7) {
            response.sendError(401);
            return;
        }

        try {
            Identity identity = client.authenticate(authorization.substring(7));
            if (identity == null || identity.id() == null
                    || !("USER".equals(identity.role()) || "ADMIN".equals(identity.role()))) {
                throw new BadCredentialsException("Invalid identity");
            }
            var authentication = new UsernamePasswordAuthenticationToken(identity, authorization,
                    List.of(new SimpleGrantedAuthority("ROLE_" + identity.role())));
            SecurityContextHolder.getContext().setAuthentication(authentication);
            chain.doFilter(request, response);
        } catch (AuthenticationException exception) {
            SecurityContextHolder.clearContext();
            response.sendError(401);
        }
    }
}
