package semchishin.dateinviteservice.service;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import semchishin.dateinviteservice.domain.Invitation;
import semchishin.dateinviteservice.domain.InvitationAnswer;
import semchishin.dateinviteservice.domain.Step;
import semchishin.dateinviteservice.repository.InvitationRepository;
import semchishin.dateinviteservice.support.ApiException;
import semchishin.dateinviteservice.web.dto.AdminView;
import semchishin.dateinviteservice.web.dto.AnswerRequest;
import semchishin.dateinviteservice.web.dto.InvitationState;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InvitationService {

    public static final String YES = "yes";
    public static final String CUSTOM_PLACE = "custom";

    private static final Logger log = LoggerFactory.getLogger(InvitationService.class);
    private static final int MAX_NOTE_LENGTH = 200;

    private final InvitationRepository invitations;
    private final ContentService content;
    private final Clock clock;

    @Transactional
    public InvitationState open() {
        return InvitationState.of(create());
    }

    @Transactional(readOnly = true)
    public Optional<InvitationState> current(String token) {
        return find(token).map(InvitationState::of);
    }

    @Transactional
    public InvitationState confirm(String token, List<AnswerRequest> answers) {
        Invitation invitation = find(token)
                .orElseThrow(() -> ApiException.notFound("Приглашение ещё не открыто"));
        if (invitation.isCompleted()) {
            log.info("Подтверждение пропущено: приглашение {} уже сохранено", invitation.getId());
            return InvitationState.of(invitation);
        }

        Map<Step, AnswerRequest> byStep = byStep(answers);
        List<Answer> validated = new ArrayList<>();
        for (Step step : Step.values()) {
            validated.add(validate(step, byStep.get(step).value(), byStep.get(step).note()));
        }

        Instant now = clock.instant();
        for (int i = 0; i < Step.values().length; i += 1) {
            Answer answer = validated.get(i);
            InvitationAnswer entity = new InvitationAnswer();
            entity.setStep(Step.values()[i]);
            entity.setValue(answer.value());
            entity.setNote(answer.note());
            entity.setCreatedAt(now);
            entity.setUpdatedAt(now);
            invitation.addAnswer(entity);
            log.info("Ответ «{}» принят", Step.values()[i].key());
        }
        invitation.setUpdatedAt(now);
        invitation.setCompletedAt(now);
        log.info("Приглашение {} подтверждено в {}", invitation.getId(), now);
        return InvitationState.of(invitation);
    }

    @Transactional(readOnly = true)
    public AdminView all() {
        List<InvitationState> states = invitations.findAllByOrderByCreatedAtDesc().stream()
                .map(InvitationState::of)
                .toList();
        log.info("Админка запросила список приглашений: всего {}", states.size());
        return AdminView.of(states);
    }

    private Map<Step, AnswerRequest> byStep(List<AnswerRequest> answers) {
        if (answers == null || answers.size() != Step.values().length) {
            throw ApiException.badRequest("Ответь на все вопросы");
        }
        Map<Step, AnswerRequest> byStep = new EnumMap<>(Step.class);
        for (AnswerRequest answer : answers) {
            if (answer == null || answer.step() == null) {
                throw ApiException.badRequest("Не указан шаг");
            }
            if (byStep.put(answer.step(), answer) != null) {
                throw ApiException.badRequest("Повторяющийся шаг: " + answer.step().key());
            }
        }
        if (byStep.size() != Step.values().length) {
            throw ApiException.badRequest("Ответь на все вопросы");
        }
        return byStep;
    }

    private Invitation create() {
        Instant now = clock.instant();
        Invitation invitation = new Invitation();
        invitation.setId(UUID.randomUUID());
        invitation.setCreatedAt(now);
        invitation.setUpdatedAt(now);
        Invitation saved = invitations.save(invitation);
        log.info("Новое приглашение {} создано в {}", saved.getId(), now);
        return saved;
    }

    private Optional<Invitation> find(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        try {
            return invitations.findById(UUID.fromString(token.trim()));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private Answer validate(Step step, String value, String note) {
        return switch (step) {
            case AGREE -> agree(value);
            case PLACE -> place(value, note);
            case DATE -> date(value);
            case TIME -> time(value);
        };
    }

    private Answer agree(String value) {
        if (!YES.equalsIgnoreCase(trimmed(value))) {
            throw ApiException.badRequest(content.wrongAnswerMessage());
        }
        return new Answer(YES, null);
    }

    private Answer place(String value, String note) {
        String requested = trimmed(value);
        if (CUSTOM_PLACE.equalsIgnoreCase(requested)) {
            String custom = trimmed(note);
            if (custom == null || custom.isEmpty()) {
                throw ApiException.badRequest("Напиши, куда хочешь пойти");
            }
            if (custom.length() > MAX_NOTE_LENGTH) {
                throw ApiException.badRequest("Слишком длинный вариант места");
            }
            return new Answer(CUSTOM_PLACE, custom);
        }
        if (requested == null) {
            throw ApiException.badRequest("Выбери место");
        }
        return content.place(requested)
                .map(item -> new Answer(item.id(), null))
                .orElseThrow(() -> ApiException.badRequest("Выбери один из вариантов места"));
    }

    private Answer date(String value) {
        LocalDate requested;
        try {
            requested = LocalDate.parse(trimmed(value));
        } catch (RuntimeException e) {
            throw ApiException.badRequest("Выбери дату в календаре");
        }
        LocalDate today = LocalDate.now(clock);
        if (requested.isBefore(today)) {
            throw ApiException.badRequest("Эта дата уже прошла");
        }
        if (requested.isAfter(today.plusDays(content.maxAdvanceDays()))) {
            throw ApiException.badRequest("Эта дата слишком далеко");
        }
        if (content.excludedDates().contains(requested)
                || content.excludedWeekdays().contains(requested.getDayOfWeek().getValue() % 7)) {
            throw ApiException.badRequest("В этот день я не смогу");
        }
        return new Answer(requested.toString(), null);
    }

    private Answer time(String value) {
        return content.time(value)
                .map(option -> new Answer(option.label(), null))
                .orElseThrow(() -> ApiException.badRequest("Выбери время из списка"));
    }

    private static String trimmed(String value) {
        return value == null ? null : value.trim();
    }

    private record Answer(String value, String note) {
    }
}
