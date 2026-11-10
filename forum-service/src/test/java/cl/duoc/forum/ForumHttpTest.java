package cl.duoc.forum;

import cl.duoc.forum.IdentityClient;
import cl.duoc.forum.Identity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {"spring.datasource.url=jdbc:h2:mem:forum;MODE=Oracle;DB_CLOSE_DELAY=-1", "spring.datasource.driver-class-name=org.h2.Driver", "spring.jpa.hibernate.ddl-auto=create", "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"})
class ForumHttpTest {
    @Autowired TestRestTemplate http;
    @MockBean IdentityClient identities;

    @Test void categoryCrudRequiresAdminAndTopicsRequireAuthentication() {
        when(identities.authenticate("admin")).thenReturn(new Identity(91L,"boss","ADMIN"));
        when(identities.authenticate("member")).thenReturn(new Identity(17L,"alice","USER"));
        ResponseEntity<String> noAuth = http.getForEntity("/api/categories", String.class);
        assertThat(noAuth.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        ResponseEntity<String> forbidden = http.exchange("/api/categories", HttpMethod.POST, request("member", "{\"name\":\"General\"}"), String.class);
        assertThat(forbidden.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        ResponseEntity<String> created = http.exchange("/api/categories", HttpMethod.POST, request("admin", "{\"name\":\"General\"}"), String.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        ResponseEntity<String> topic = http.exchange("/api/topics", HttpMethod.POST, request("member", "{\"categoryId\":1,\"title\":\"Hello\",\"body\":\"World\",\"userId\":999}"), String.class);
        assertThat(topic.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(topic.getBody()).contains("\"authorId\":17");
        ResponseEntity<String> userCategoryDelete = http.exchange("/api/categories/1", HttpMethod.DELETE, auth("member"), String.class);
        assertThat(userCategoryDelete.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test void topicOwnerAndAdminBoundariesAndComments() {
        when(identities.authenticate("admin")).thenReturn(new Identity(91L,"boss","ADMIN"));
        when(identities.authenticate("alice")).thenReturn(new Identity(17L,"alice","USER"));
        when(identities.authenticate("bob")).thenReturn(new Identity(18L,"bob","USER"));
        http.exchange("/api/categories",HttpMethod.POST,request("admin","{\"name\":\"Help\"}"),String.class);
        ResponseEntity<String> made=http.exchange("/api/topics",HttpMethod.POST,request("alice","{\"categoryId\":1,\"title\":\"Need help\",\"body\":\"Question\"}"),String.class);
        assertThat(made.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(http.exchange("/api/topics/1",HttpMethod.PUT,request("bob","{\"title\":\"Hijack\",\"body\":\"x\",\"categoryId\":1}"),String.class).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(http.exchange("/api/topics/1/comments",HttpMethod.POST,request("bob","{\"body\":\"Reply\"}"),String.class).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(http.exchange("/api/topics/1/comments/1",HttpMethod.DELETE,auth("alice"),String.class).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(http.exchange("/api/topics/1",HttpMethod.PUT,request("alice","{\"title\":\"Updated\",\"body\":\"Question\",\"categoryId\":1}"),String.class).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(http.exchange("/api/topics?search=Updated",HttpMethod.GET,auth("alice"),String.class).getBody()).contains("Updated");
        assertThat(http.exchange("/api/topics/1",HttpMethod.DELETE,auth("admin"),String.class).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(http.exchange("/api/topics/1",HttpMethod.GET,auth("alice"),String.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(http.exchange("/api/topics/1/comments",HttpMethod.GET,auth("alice"),String.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    private HttpEntity<String> request(String token,String body){ HttpHeaders h=headers(token);h.setContentType(MediaType.APPLICATION_JSON);return new HttpEntity<>(body,h); }
    private HttpEntity<Void> auth(String token){return new HttpEntity<>(headers(token));}
    private HttpHeaders headers(String token){HttpHeaders h=new HttpHeaders();h.setBearerAuth(token);return h;}
}
