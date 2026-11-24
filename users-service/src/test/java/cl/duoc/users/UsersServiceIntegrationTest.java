package cl.duoc.users;

import cl.duoc.users.user.AppUser;
import cl.duoc.users.user.Role;
import cl.duoc.users.user.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.time.Instant;
import java.util.Base64;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.mockito.ArgumentCaptor;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import java.util.Date;
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
    @Autowired cl.duoc.users.auth.PasswordResetTokenRepository resetTokens;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired ObjectMapper objectMapper;
    @org.springframework.boot.test.mock.mockito.MockBean JavaMailSender mailSender;

    @BeforeEach
    void resetUsers() {
        reset(mailSender);
        resetTokens.deleteAll();
        users.deleteAll();
        users.save(new AppUser("admin", "admin@example.test", passwordEncoder.encode("Admin-pass-123!"), Role.ADMIN));
        users.save(new AppUser("member", "member@example.test", passwordEncoder.encode("Member-pass-123!"), Role.USER));
    }

    @Test
    void publicRegistrationCreatesOnlyUserAndEnforcesPasswordRules() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"registered\",\"email\":\"registered@example.test\",\"password\":\"Strong-pass-123!\",\"role\":\"ADMIN\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("registered"))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
        login("registered", "Strong-pass-123!");
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"other1\",\"email\":\"other1@example.test\",\"password\":\"short1!\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"other2\",\"email\":\"other2@example.test\",\"password\":\"alllowercase1!\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"other3\",\"email\":\"other3@example.test\",\"password\":\"ALLUPPERCASE1!\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"other4\",\"email\":\"other4@example.test\",\"password\":\"NoDigitsHere!\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"other5\",\"email\":\"other5@example.test\",\"password\":\"NoSpecial123\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"registered\",\"email\":\"duplicate@example.test\",\"password\":\"Strong-pass-123!\"}"))
                .andExpect(status().isConflict());
        assertThat(users.findByUsername("other1")).isEmpty();
    }

    @Test
    void passwordRecoveryIsNonEnumeratingAndUsesSingleUseExpiringToken() throws Exception {
        String response = mvc.perform(post("/api/auth/recover").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"member@example.test\"}"))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
        assertThat(response).doesNotContain("token", "member@example.test");
        ArgumentCaptor<SimpleMailMessage> message = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(message.capture());
        assertThat(message.getValue().getTo()).containsExactly("member@example.test");
        String body = message.getValue().getText();
        assertThat(body).isNotNull();
        String token = body.substring(body.lastIndexOf("#resetToken=") + "#resetToken=".length()).trim();
        assertThat(token).hasSizeGreaterThanOrEqualTo(40);
        mvc.perform(post("/api/auth/recover").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"unknown@example.test\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.message").value("If the account exists, recovery instructions will be sent."));

        mvc.perform(post("/api/auth/reset-password").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ResetRequest(token, "Changed-pass-123!"))))
                .andExpect(status().isNoContent());
        login("member", "Changed-pass-123!");
        mvc.perform(post("/api/auth/reset-password").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ResetRequest(token, "Changed-again-123!"))))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"member\",\"password\":\"Member-pass-123!\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void authenticatedUserCanOnlyReadAndUpdateOwnProfileWithoutChangingRole() throws Exception {
        String token = login("member", "Member-pass-123!");
        mvc.perform(get("/api/users/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("member"))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
        mvc.perform(put("/api/users/me").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"member\",\"email\":\"updated-member@example.test\",\"role\":\"ADMIN\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("member"))
                .andExpect(jsonPath("$.email").value("updated-member@example.test"))
                .andExpect(jsonPath("$.role").value("USER"));
        mvc.perform(get("/api/users/me")).andExpect(status().isUnauthorized());
        mvc.perform(put("/api/users/me").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"anonymous\",\"email\":\"anonymous@example.test\"}"))
                .andExpect(status().isUnauthorized());
        assertThat(users.findByUsername("member")).isPresent();
    }

    @Test
    void usernameCannotBeChangedToPreventOldTokenRebindingAfterUsernameReuse() throws Exception {
        String oldToken = login("member", "Member-pass-123!");
        mvc.perform(put("/api/users/me").header("Authorization", "Bearer " + oldToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"reassigned-name\",\"email\":\"member@example.test\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/users/me").header("Authorization", "Bearer " + oldToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("member"));
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"member\",\"email\":\"reused@example.test\",\"password\":\"Strong-pass-123!\"}"))
                .andExpect(status().isConflict());
        assertThat(users.findByUsername("member")).isPresent();
        assertThat(users.findByUsername("reassigned-name")).isEmpty();
    }

    @Test
    void deletedAccountTokenCannotAuthenticateReRegisteredAdminAccount() throws Exception {
        String oldToken = login("member", "Member-pass-123!");
        String adminToken = login("admin", "Admin-pass-123!");
        long memberId = findUserId(adminToken, "member");
        mvc.perform(delete("/api/users/{id}", memberId).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());
        String registration = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"member\",\"email\":\"replacement@example.test\",\"password\":\"Strong-pass-123!\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long replacementId = objectMapper.readTree(registration).get("id").asLong();
        mvc.perform(put("/api/users/{id}", replacementId).header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"member\",\"email\":\"replacement@example.test\",\"role\":\"ADMIN\"}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + oldToken))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/users").header("Authorization", "Bearer " + oldToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void adminRenameAndUsernameReassignmentKeepOldTokenOnOriginalAccount() throws Exception {
        String oldToken = login("member", "Member-pass-123!");
        String adminToken = login("admin", "Admin-pass-123!");
        long memberId = findUserId(adminToken, "member");
        mvc.perform(put("/api/users/{id}", memberId).header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"renamed-member\",\"email\":\"member@example.test\",\"role\":\"USER\"}"))
                .andExpect(status().isOk());
        String registration = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"member\",\"email\":\"replacement@example.test\",\"password\":\"Strong-pass-123!\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long replacementId = objectMapper.readTree(registration).get("id").asLong();
        mvc.perform(put("/api/users/{id}", replacementId).header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"member\",\"email\":\"replacement@example.test\",\"role\":\"ADMIN\"}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + oldToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("renamed-member"))
                .andExpect(jsonPath("$.role").value("USER"));
        mvc.perform(get("/api/users").header("Authorization", "Bearer " + oldToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void legacyUsernameSubjectTokenIsRejectedAfterUsernameReassignment() throws Exception {
        String adminToken = login("admin", "Admin-pass-123!");
        long memberId = findUserId(adminToken, "member");
        String legacyToken = legacyUsernameToken("member");
        mvc.perform(delete("/api/users/{id}", memberId).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"member\",\"email\":\"replacement@example.test\",\"password\":\"Strong-pass-123!\"}"))
                .andExpect(status().isCreated());
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + legacyToken))
                .andExpect(status().isUnauthorized());
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

    private long findUserId(String adminToken, String username) throws Exception {
        JsonNode allUsers = objectMapper.readTree(mvc.perform(get("/api/users").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        return java.util.stream.StreamSupport.stream(allUsers.spliterator(), false)
                .filter(user -> user.get("username").asText().equals(username))
                .findFirst().orElseThrow().get("id").asLong();
    }

    private String legacyUsernameToken(String username) {
        byte[] secret = Base64.getDecoder().decode("VGhpc0lzQVRlc3RPbmx5U2VjcmV0S2V5Rm9ySlZUMTIzNDU2Nzg=");
        Instant now = Instant.now();
        return Jwts.builder().subject(username).issuedAt(Date.from(now)).expiration(Date.from(now.plusSeconds(3600)))
                .signWith(Keys.hmacShaKeyFor(secret)).compact();
    }

    private String login(String username, String password) throws Exception {
        String response = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(username, password))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("token").asText();
    }

    private record LoginRequest(String username, String password) { }
    private record ResetRequest(String token, String password) { }
}
