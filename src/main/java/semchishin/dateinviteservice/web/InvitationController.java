package semchishin.dateinviteservice.web;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import semchishin.dateinviteservice.content.Content;
import semchishin.dateinviteservice.service.ContentService;
import semchishin.dateinviteservice.service.InvitationService;
import semchishin.dateinviteservice.support.ApiException;
import semchishin.dateinviteservice.web.dto.ConfirmRequest;
import semchishin.dateinviteservice.web.dto.InvitationState;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class InvitationController {

    public static final String TOKEN_HEADER = "X-Invitation-Token";

    private final ContentService content;
    private final InvitationService invitations;

    @GetMapping("/content")
    public Content content() {
        return content.content();
    }

    @PostMapping("/invitations")
    public ResponseEntity<InvitationState> open() {
        return ResponseEntity.ok(invitations.open());
    }

    @GetMapping("/invitations/current")
    public InvitationState current(@RequestHeader(name = TOKEN_HEADER, required = false) String token) {
        return invitations.current(token)
                .orElseThrow(() -> ApiException.notFound("Приглашение ещё не открыто"));
    }

    @PostMapping("/invitations/confirm")
    public InvitationState confirm(@RequestHeader(name = TOKEN_HEADER, required = false) String token,
                                  @RequestBody ConfirmRequest body) {
        if (body == null) {
            throw ApiException.badRequest("Нет ответов");
        }
        return invitations.confirm(token, body.answers());
    }
}
