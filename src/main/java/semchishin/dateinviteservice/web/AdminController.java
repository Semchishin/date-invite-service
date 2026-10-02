package semchishin.dateinviteservice.web;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import semchishin.dateinviteservice.service.InvitationService;
import semchishin.dateinviteservice.web.dto.AdminView;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final InvitationService invitations;

    @GetMapping("/invitations")
    public AdminView invitations() {
        return invitations.all();
    }
}
