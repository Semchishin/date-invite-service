package semchishin.dateinviteservice.web.dto;

import java.util.List;

public record ConfirmRequest(List<AnswerRequest> answers) {
}
