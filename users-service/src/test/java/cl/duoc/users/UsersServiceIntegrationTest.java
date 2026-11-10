package cl.duoc.users;

import cl.duoc.users.user.AppUser;
import cl.duoc.users.user.Role;
import cl.duoc.users.user.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:users;MODE=Oracle;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "app.jwt.secret=VGhpc0lzQVRlc3RPbmx5U2VjcmV0S2V5Rm9ySlZUMTIzNDU2Nzg=",
        "app.jwt.expiration=PT1H"
})
@AutoConfigureMockMvc
class UsersServiceIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired ObjectMapper objectMapper;

    @BeforeEach
    void resetUsers() {
        users.deleteAll();
        users.save(new AppUser("admin", "admin@example.test", passwordEncoder.encode("Admin-pass-123!"), Role.ADMIN));
        users.save(new AppUser("member", "member@example.test", passwordEncoder.encode("Member-pass-123!"), Role.USER));
    }

    @Test
    void loginAndMeExposeAuthenticatedIdentityAndRoleOnly() throws Exception {
        String token = login("member", "Member-pass-123!");
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("member"))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.password").doesNotExist());
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"member\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void userCannotManageAccountsAndCannotEscalateRole() throws Exception {
        String token = login("member", "Member-pass-123!");
        String adminToken = login("admin", "Admin-pass-123!");
        JsonNode before = objectMapper.readTree(mvc.perform(get("/api/users").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        long memberId = java.util.stream.StreamSupport.stream(before.spliterator(), false)
                .filter(user -> user.get("username").asText().equals("member"))
                .findFirst().orElseThrow().get("id").asLong();
        mvc.perform(post("/api/users").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"attacker\",\"email\":\"attacker@example.test\",\"password\":\"Long-pass-123!\",\"role\":\"ADMIN\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/users/{id}", memberId).header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"member\",\"email\":\"member@example.test\",\"role\":\"ADMIN\"}"))
                .andExpect(status().isForbidden());
        JsonNode after = objectMapper.readTree(mvc.perform(get("/api/users").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(after.toString()).doesNotContain("attacker");
        mvc.perform(get("/api/users/{id}", memberId).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.role").value("USER"));
    }

    @Test
    void adminCanCreateReadUpdateAndDeleteUsersWithoutReturningPassword() throws Exception {
        String token = login("admin", "Admin-pass-123!");
        String auth = "Bearer " + token;
        String response = mvc.perform(post("/api/users").header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"new-user\",\"email\":\"new@example.test\",\"password\":\"New-pass-123!\",\"role\":\"USER\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("new-user"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        JsonNode created = objectMapper.readTree(response);
        long id = created.get("id").asLong();
        login("new-user", "New-pass-123!");

        mvc.perform(get("/api/users/{id}", id).header("Authorization", auth))
                .andExpect(status().isOk()).andExpect(jsonPath("$.email").value("new@example.test"));
        mvc.perform(put("/api/users/{id}", id).header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"new-user\",\"email\":\"updated@example.test\",\"role\":\"ADMIN\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.role").value("ADMIN"));
        mvc.perform(delete("/api/users/{id}", id).header("Authorization", auth))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/users/{id}", id).header("Authorization", auth))
                .andExpect(status().isNotFound());
    }

    private String login(String username, String password) throws Exception {
        String response = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(username, password))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("token").asText();
    }

    private record LoginRequest(String username, String password) { }
}
