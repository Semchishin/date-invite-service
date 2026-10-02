package semchishin.dateinviteservice.web.dto;

import semchishin.dateinviteservice.domain.Invitation;
import semchishin.dateinviteservice.domain.InvitationAnswer;
import semchishin.dateinviteservice.domain.Step;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public record InvitationState(UUID id,
                              Instant createdAt,
                              Instant updatedAt,
                              Instant completedAt,
                              Step currentStep,
                              List<AnswerView> answers) {

    public static InvitationState of(Invitation invitation) {
        List<AnswerView> answers = invitation.getAnswers().stream()
                .map(AnswerView::of)
                .sorted(Comparator.comparingInt(answer -> answer.step().ordinal()))
                .toList();
        return new InvitationState(
                invitation.getId(),
                invitation.getCreatedAt(),
                invitation.getUpdatedAt(),
                invitation.getCompletedAt(),
                invitation.currentStep(),
                answers);
    }

    public record AnswerView(Step step, String value, String note, Instant createdAt) {

        static AnswerView of(InvitationAnswer answer) {
            return new AnswerView(answer.getStep(), answer.getValue(), answer.getNote(), answer.getCreatedAt());
        }
    }
}
