package semchishin.dateinviteservice;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import semchishin.dateinviteservice.domain.Step;
import semchishin.dateinviteservice.repository.InvitationRepository;
import semchishin.dateinviteservice.web.dto.AnswerRequest;
import semchishin.dateinviteservice.web.dto.ConfirmRequest;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
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
class InvitationFlowTest {

    private static final String TOKEN = "X-Invitation-Token";

    private static final String AVAILABLE_DATE = "2026-05-15";
    private static final String EXCLUDED_DATE = "2026-05-20";
    private static final String EXCLUDED_WEEKDAY = "2026-05-16";
    private static final String PAST_DATE = "2026-04-30";
    private static final String TOO_FAR_DATE = "2026-08-01";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private InvitationRepository invitations;

    private String token;

    @BeforeEach
    void startInvitation() throws Exception {
        invitations.deleteAll();
        token = openInvitation();
    }

    private String openInvitation() throws Exception {
        return objectMapper.readTree(mockMvc.perform(post("/api/invitations"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString())
                .path("id").asText();
    }

    @Test
    @DisplayName("Контент отдаётся из файла")
    void contentIsServedFromFile() throws Exception {
        mockMvc.perform(get("/api/content"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.places.length()").value(3))
                .andExpect(jsonPath("$.schedule.times[0]").value("18:00"));
    }

    @Test
    @DisplayName("Новое приглашение ждёт ответов")
    void newInvitationStartsEmpty() throws Exception {
        JsonNode state = readState();
        assertThat(state.path("currentStep").asText()).isEqualTo(Step.AGREE.key());
        assertThat(state.path("answers").size()).isZero();
        assertThat(state.path("completedAt").isNull()).isTrue();
    }

    @Test
    @DisplayName("До подтверждения в базе пусто")
    void nothingIsStoredBeforeConfirm() throws Exception {
        assertThat(invitations.findAllByOrderByCreatedAtDesc().get(0).getAnswers()).isEmpty();
    }

    @Test
    @DisplayName("Ответ «Нет» не принимается")
    void negativeAnswerIsRejected() throws Exception {
        confirm(answers(Step.AGREE, "no", null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Произошла ошибка. Выберите правильный вариант ответа"));

        assertThat(readState().path("answers").size()).isZero();
        assertThat(readState().path("completedAt").isNull()).isTrue();
    }

    @Test
    @DisplayName("Нужно ответить на все вопросы")
    void incompleteAnswersAreRejected() throws Exception {
        confirm(List.of(new AnswerRequest(Step.AGREE, "yes", null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Ответь на все вопросы"));

        confirm(List.of(
                new AnswerRequest(Step.AGREE, "yes", null),
                new AnswerRequest(Step.PLACE, "cafe", null),
                new AnswerRequest(Step.DATE, AVAILABLE_DATE, null),
                new AnswerRequest(Step.DATE, AVAILABLE_DATE, null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Повторяющийся шаг: date"));
    }

    @Test
    @DisplayName("Подтверждение сохраняет все ответы разом")
    void confirmStoresAllAnswers() throws Exception {
        confirm(answers(Step.TIME, "20:00", null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completedAt").isNotEmpty());

        JsonNode state = readState();
        assertThat(state.path("currentStep").isNull()).isTrue();
        assertThat(state.path("completedAt").isNull()).isFalse();
        assertThat(state.path("answers").size()).isEqualTo(4);

        List<String> stored = invitations.findAllByOrderByCreatedAtDesc().get(0).getAnswers().stream()
                .map(item -> item.getStep().key() + "=" + item.getValue())
                .toList();
        assertThat(stored).containsExactly("agree=yes", "place=cafe", "date=" + AVAILABLE_DATE, "time=20:00");
    }

    @Test
    @DisplayName("Повторное подтверждение не ломает данные")
    void confirmIsIdempotent() throws Exception {
        confirm(answers(Step.TIME, "20:00", null)).andExpect(status().isOk());
        confirm(answers(Step.TIME, "18:00", null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answers[3].value").value("20:00"));

        assertThat(invitations.findAllByOrderByCreatedAtDesc().get(0).getAnswers()).hasSize(4);
    }

    @Test
    @DisplayName("Исключённые и некорректные даты не принимаются")
    void dateValidation() throws Exception {
        confirm(answers(Step.DATE, PAST_DATE, null)).andExpect(status().isBadRequest());
        confirm(answers(Step.DATE, TOO_FAR_DATE, null)).andExpect(status().isBadRequest());
        confirm(answers(Step.DATE, "не дата", null)).andExpect(status().isBadRequest());
        confirm(answers(Step.DATE, EXCLUDED_DATE, null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("В этот день я не смогу"));
        confirm(answers(Step.DATE, EXCLUDED_WEEKDAY, null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("В этот день я не смогу"));

        assertThat(invitations.findAllByOrderByCreatedAtDesc().get(0).getAnswers()).isEmpty();
    }

    @Test
    @DisplayName("Время берётся только из списка")
    void timeValidation() throws Exception {
        confirm(answers(Step.TIME, "23:00", null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Выбери время из списка"));
    }

    @Test
    @DisplayName("Свой вариант места сохраняется вместе с комментарием")
    void customPlaceIsStored() throws Exception {
        confirm(answers(Step.PLACE, "custom", "  На набережной  "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answers[1].step").value("place"))
                .andExpect(jsonPath("$.answers[1].value").value("custom"))
                .andExpect(jsonPath("$.answers[1].note").value("На набережной"));
    }

    @Test
    @DisplayName("Свой вариант без текста не принимается")
    void customPlaceRequiresText() throws Exception {
        confirm(answers(Step.PLACE, "custom", "   "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Напиши, куда хочешь пойти"));
    }

    @Test
    @DisplayName("Неизвестное место не принимается")
    void unknownPlaceIsRejected() throws Exception {
        confirm(answers(Step.PLACE, "дискотека", null)).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("У разных посетителей разные приглашения")
    void visitorsAreIsolated() throws Exception {
        confirm(answers(Step.TIME, "20:00", null)).andExpect(status().isOk());

        String other = openInvitation();

        assertThat(invitations.findAllByOrderByCreatedAtDesc()).hasSize(2);
        assertThat(objectMapper.readTree(mockMvc.perform(get("/api/invitations/current").header(TOKEN, other))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString())
                .path("currentStep").asText()).isEqualTo(Step.AGREE.key());
    }

    @Test
    @DisplayName("Без заголовка приглашение не выдаётся и не подтверждается")
    void missingTokenIsRejected() throws Exception {
        mockMvc.perform(get("/api/invitations/current")).andExpect(status().isNotFound());
        mockMvc.perform(post("/api/invitations/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ConfirmRequest(answers(Step.TIME, "20:00", null)))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Приглашение ещё не открыто"));
    }

    @Test
    @DisplayName("Незнакомый идентификатор не открывает чужое приглашение")
    void unknownTokenIsRejected() throws Exception {
        mockMvc.perform(get("/api/invitations/current").header(TOKEN, UUID.randomUUID()))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/invitations/current").header(TOKEN, "не-uuid"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Сайт не выдаёт куки")
    void noCookiesAreIssued() throws Exception {
        assertThat(mockMvc.perform(get("/")).andReturn().getResponse().getHeaders("Set-Cookie")).isEmpty();
        assertThat(mockMvc.perform(get("/api/content")).andReturn().getResponse().getHeaders("Set-Cookie")).isEmpty();
        assertThat(mockMvc.perform(get("/api/invitations/current").header(TOKEN, token))
                .andReturn().getResponse().getHeaders("Set-Cookie")).isEmpty();
        assertThat(mockMvc.perform(post("/api/invitations"))
                .andReturn().getResponse().getHeaders("Set-Cookie")).isEmpty();
        assertThat(confirm(answers(Step.TIME, "20:00", null)).andReturn().getResponse().getHeaders("Set-Cookie")).isEmpty();
    }

    @Test
    @DisplayName("Непонятный шаг отклоняется")
    void unknownStepIsRejected() throws Exception {
        mockMvc.perform(post("/api/invitations/confirm")
                        .header(TOKEN, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("answers", List.of(Map.of("step", "hack", "value", "yes"))))))
                .andExpect(status().isBadRequest());
    }

    private List<AnswerRequest> answers(Step step, String value, String note) {
        List<AnswerRequest> answers = new ArrayList<>();
        answers.add(new AnswerRequest(Step.AGREE, "yes", null));
        answers.add(new AnswerRequest(Step.PLACE, "cafe", null));
        answers.add(new AnswerRequest(Step.DATE, AVAILABLE_DATE, null));
        answers.add(new AnswerRequest(Step.TIME, "20:00", null));
        for (int i = 0; i < answers.size(); i += 1) {
            if (answers.get(i).step() == step) {
                answers.set(i, new AnswerRequest(step, value, note));
            }
        }
        return answers;
    }

    private org.springframework.test.web.servlet.ResultActions confirm(List<AnswerRequest> answers) throws Exception {
        return mockMvc.perform(post("/api/invitations/confirm")
                .header(TOKEN, token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new ConfirmRequest(answers))));
    }

    private JsonNode readState() throws Exception {
        return objectMapper.readTree(mockMvc.perform(get("/api/invitations/current").header(TOKEN, token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }
}
