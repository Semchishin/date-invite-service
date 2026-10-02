package semchishin.dateinviteservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import semchishin.dateinviteservice.domain.InvitationAnswer;

public interface InvitationAnswerRepository extends JpaRepository<InvitationAnswer, Long> {
}
