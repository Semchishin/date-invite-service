package semchishin.dateinviteservice.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;

public enum Step {

    AGREE("agree"),
    PLACE("place"),
    DATE("date"),
    TIME("time");

    private final String key;

    Step(String key) {
        this.key = key;
    }

    @JsonValue
    public String key() {
        return key;
    }

    @JsonCreator
    public static Step fromKey(String value) {
        return Arrays.stream(values())
                .filter(step -> step.key.equalsIgnoreCase(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Неизвестный шаг: " + value));
    }
}
