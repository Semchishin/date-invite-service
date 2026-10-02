package semchishin.dateinviteservice.web.dto;

import semchishin.dateinviteservice.domain.Step;

public record AnswerRequest(Step step, String value, String note) {
}
