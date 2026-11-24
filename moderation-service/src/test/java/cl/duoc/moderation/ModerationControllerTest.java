package cl.duoc.moderation;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ModerationController.class)
@org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc(addFilters = false)
class ModerationControllerTest {
    @Autowired MockMvc http;
    @MockBean IdentityClient identities;
    @MockBean ForumClient forum;

    @Test void requiresBearerIdentityAndRejectsForgedRole() throws Exception {
        http.perform(delete("/api/moderation/topics/12").header("X-Role", "ADMIN"))
                .andExpect(status().isUnauthorized());
        when(identities.authenticate("user-token")).thenReturn(new Identity(7L, "member", "USER"));
        http.perform(delete("/api/moderation/topics/12").header("Authorization", "Bearer user-token")
                        .header("X-Role", "ADMIN"))
                .andExpect(status().isForbidden());
        verify(forum, never()).banTopic(12L, "user-token");
    }

    @Test void adminTopicsAndCommentsAreDelegatedToForumApi() throws Exception {
        when(identities.authenticate("admin-token")).thenReturn(new Identity(1L, "root", "ADMIN"));
        http.perform(delete("/api/moderation/topics/12").header("Authorization", "Bearer admin-token"))
                .andExpect(status().isNoContent());
        http.perform(delete("/api/moderation/topics/12/comments/4").header("Authorization", "Bearer admin-token"))
                .andExpect(status().isNoContent());
        verify(forum).banTopic(12L, "admin-token");
        verify(forum).banComment(12L, 4L, "admin-token");
    }
}
