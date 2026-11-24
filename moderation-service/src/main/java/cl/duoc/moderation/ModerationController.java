package cl.duoc.moderation;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

record Identity(Long id, String username, String role) {}

class IdentityClient {
    private final RestTemplate http;
    @Value("${users-service.url:http://users-service:8081}") private String base;

    IdentityClient(RestTemplate http) { this.http = http; }

    Identity authenticate(String token) {
        try {
            return http.exchange(base + "/api/auth/me", HttpMethod.GET,
                    new HttpEntity<>(headers(token)), Identity.class).getBody();
        } catch (RestClientException exception) {
            return null;
        }
    }

    private HttpHeaders headers(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }
}

class ForumClient {
    private final RestTemplate http;
    @Value("${forum-service.url:http://forum-service:8082}") private String base;

    ForumClient(RestTemplate http) { this.http = http; }

    void banTopic(Long topicId, String token) {
        delete("/api/topics/" + topicId, token);
    }

    void banComment(Long topicId, Long commentId, String token) {
        delete("/api/topics/" + topicId + "/comments/" + commentId, token);
    }

    private void delete(String path, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        http.exchange(base + path, HttpMethod.DELETE, new HttpEntity<>(headers), Void.class);
    }
}

@RestController
@RequestMapping("/api/moderation")
class ModerationController {
    private final IdentityClient identities;
    private final ForumClient forum;

    ModerationController(IdentityClient identities, ForumClient forum) {
        this.identities = identities;
        this.forum = forum;
    }

    @DeleteMapping("/topics/{topicId}")
    ResponseEntity<Void> banTopic(@PathVariable Long topicId,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return moderate(authorization, token -> forum.banTopic(topicId, token));
    }

    @DeleteMapping("/topics/{topicId}/comments/{commentId}")
    ResponseEntity<Void> banComment(@PathVariable Long topicId, @PathVariable Long commentId,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return moderate(authorization, token -> forum.banComment(topicId, commentId, token));
    }

    private ResponseEntity<Void> moderate(String authorization, java.util.function.Consumer<String> action) {
        if (authorization == null || !authorization.startsWith("Bearer ") || authorization.length() == 7) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        String token = authorization.substring(7);
        Identity identity = identities.authenticate(token);
        if (identity == null || identity.id() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        if (!"ADMIN".equals(identity.role())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        action.accept(token);
        return ResponseEntity.noContent().build();
    }
}
