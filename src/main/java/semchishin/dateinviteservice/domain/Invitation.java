package semchishin.dateinviteservice.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "invitation")
public class Invitation {

    @Id
    private UUID id;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @OrderBy("id ASC")
    @OneToMany(mappedBy = "invitation", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<InvitationAnswer> answers = new ArrayList<>();

    public void addAnswer(InvitationAnswer answer) {
        answer.setInvitation(this);
        answers.add(answer);
    }

    public Optional<InvitationAnswer> answerFor(Step step) {
        return answers.stream()
                .filter(answer -> answer.getStep() == step)
                .findFirst();
    }

    public boolean isCompleted() {
        return currentStep() == null;
    }

    public Step currentStep() {
        for (Step step : Step.values()) {
            if (answerFor(step).isEmpty()) {
                return step;
            }
        }
        return null;
    }
}
