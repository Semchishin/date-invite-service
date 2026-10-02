package semchishin.dateinviteservice;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import semchishin.dateinviteservice.domain.Step;
import semchishin.dateinviteservice.repository.InvitationRepository;
import semchishin.dateinviteservice.web.dto.AnswerRequest;
import semchishin.dateinviteservice.web.dto.ConfirmRequest;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "app.content-file=src/test/resources/test-content.json",
        "app.admin.username=admin",
        "app.admin.password=secret",
        "spring.docker.compose.enabled=false"
})
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, FixedClockConfiguration.class})
class AdminPageTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private InvitationRepository invitations;

    @BeforeEach
    void cleanDatabase() {
        invitations.deleteAll();
    }

    @Test
    @DisplayName("Страница приглашения открыта всем")
    void invitationPageIsPublic() throws Exception {
        mockMvc.perform(get("/")).andExpect(status().isOk());
        mockMvc.perform(get("/app.js")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("Без логина админка и её api закрыты")
    void adminRequiresBasicAuth() throws Exception {
        mockMvc.perform(get("/admin")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/admin.html")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/admin/invitations")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("С логином админка показывает ответы")
    void adminSeesAnswers() throws Exception {
        String token = objectMapper.readTree(mockMvc.perform(post("/api/invitations"))
                        .andReturn().getResponse().getContentAsString())
                .path("id").asText();
        mockMvc.perform(post("/api/invitations/confirm")
                        .header("X-Invitation-Token", token)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new ConfirmRequest(List.of(
                                new AnswerRequest(Step.AGREE, "yes", null),
                                new AnswerRequest(Step.PLACE, "park", null),
                                new AnswerRequest(Step.DATE, "2026-05-15", null),
                                new AnswerRequest(Step.TIME, "20:00", null))))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/admin.html").with(httpBasic("admin", "secret"))).andExpect(status().isOk());
        mockMvc.perform(get("/api/admin/invitations").with(httpBasic("admin", "secret")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.total").value(1))
                .andExpect(jsonPath("$.summary.completed").value(1))
                .andExpect(jsonPath("$.invitations[0].answers[0].value").value("yes"))
                .andExpect(jsonPath("$.invitations[0].answers[1].value").value("park"));
    }

    @Test
    @DisplayName("Незавершённое приглашение видно в админке")
    void adminSeesUnfinishedInvitation() throws Exception {
        mockMvc.perform(post("/api/invitations")).andExpect(status().isOk());

        mockMvc.perform(get("/api/admin/invitations").with(httpBasic("admin", "secret")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.total").value(1))
                .andExpect(jsonPath("$.summary.completed").value(0))
                .andExpect(jsonPath("$.invitations[0].currentStep").value("agree"))
                .andExpect(jsonPath("$.invitations[0].answers.length()").value(0));
    }

    @Test
    @DisplayName("С неверным паролем админка не открывается")
    void adminRejectsWrongPassword() throws Exception {
        mockMvc.perform(get("/api/admin/invitations").with(httpBasic("admin", "wrong")))
                .andExpect(status().isUnauthorized());
    }
}
