package semchishin.dateinviteservice.web.dto;

import java.util.List;

public record AdminView(Summary summary, List<InvitationState> invitations) {

    public static AdminView of(List<InvitationState> invitations) {
        long completed = invitations.stream()
                .filter(invitation -> invitation.completedAt() != null)
                .count();
        return new AdminView(new Summary(invitations.size(), completed), invitations);
    }

    public record Summary(int total, long completed) {
    }
}
