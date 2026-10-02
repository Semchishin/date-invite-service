package semchishin.dateinviteservice.repository;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import semchishin.dateinviteservice.domain.Invitation;

import java.util.List;
import java.util.UUID;

public interface InvitationRepository extends JpaRepository<Invitation, UUID> {

    @EntityGraph(attributePaths = "answers")
    List<Invitation> findAllByOrderByCreatedAtDesc();
}
