package semchishin.dateinviteservice.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import semchishin.dateinviteservice.config.AppProperties;
import semchishin.dateinviteservice.content.Content;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Slf4j
@Service
public class ContentService {

    private static final int DEFAULT_MAX_ADVANCE_DAYS = 90;

    private final AppProperties properties;
    private final ObjectMapper objectMapper;

    private volatile Cache cache;

    public ContentService(AppProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public Content content() {
        return load().content();
    }

    public Optional<Content.Place> place(String id) {
        return content().placeById(id);
    }

    public String wrongAnswerMessage() {
        String message = content().texts().errorWrongAnswer();
        return message == null || message.isBlank() ? "Произошла ошибка. Выберите правильный вариант ответа" : message;
    }

    public Set<LocalDate> excludedDates() {
        return load().excludedDates();
    }

    public Set<Integer> excludedWeekdays() {
        return load().excludedWeekdays();
    }

    public int maxAdvanceDays() {
        return load().maxAdvanceDays();
    }

    public Optional<TimeOption> time(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        LocalTime requested = parseTime(value.trim());
        if (requested == null) {
            return Optional.empty();
        }
        return load().times().stream()
                .filter(option -> option.time().equals(requested))
                .findFirst();
    }

    public List<TimeOption> times() {
        return load().times();
    }

    private Cache load() {
        Cache current = cache;
        Source source = resolveSource();
        if (current != null && current.source().stamp().equals(source.stamp())) {
            return current;
        }
        Content content = read(source);
        Cache fresh = new Cache(source, toCache(content, source));
        cache = fresh;
        return fresh;
    }

    private Source resolveSource() {
        Path path = Path.of(properties.contentFileOrDefault()).toAbsolutePath();
        if (Files.isReadable(path)) {
            try {
                return new Source(path, Files.getLastModifiedTime(path).toMillis() + ":" + Files.size(path));
            } catch (IOException e) {
                log.warn("Не удалось прочитать {}: {}", path, e.getMessage());
            }
        }
        ClassPathResource resource = new ClassPathResource("content.json");
        if (resource.exists()) {
            return new Source(null, "classpath:" + resource.getDescription());
        }
        throw new IllegalStateException("Не найден файл контента: " + properties.contentFileOrDefault()
                + ". Укажи путь через переменную CONTENT_FILE или см. data/content.json");
    }

    private Content read(Source source) {
        try {
            if (source.path() != null) {
                return objectMapper.readValue(Files.readString(source.path()), Content.class);
            }
            try (InputStream input = new ClassPathResource("content.json").getInputStream()) {
                return objectMapper.readValue(input, Content.class);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Не удалось разобрать файл контента " + source.path(), e);
        }
    }

    private CacheParts toCache(Content content, Source source) {
        Content.Schedule schedule = content.schedule();
        Set<LocalDate> excludedDates = new LinkedHashSet<>();
        Set<Integer> excludedWeekdays = new LinkedHashSet<>();
        List<TimeOption> times = new ArrayList<>();
        int maxAdvanceDays = DEFAULT_MAX_ADVANCE_DAYS;

        if (schedule != null) {
            if (schedule.excludedDates() != null) {
                for (String raw : schedule.excludedDates()) {
                    excludedDates.add(parseDate(raw, source));
                }
            }
            if (schedule.excludedWeekdays() != null) {
                for (Integer weekday : schedule.excludedWeekdays()) {
                    if (weekday != null) {
                        excludedWeekdays.add(weekday);
                    }
                }
            }
            if (schedule.maxAdvanceDays() != null) {
                maxAdvanceDays = schedule.maxAdvanceDays();
            }
            if (schedule.times() != null) {
                for (String raw : schedule.times()) {
                    LocalTime time = parseTime(raw);
                    if (time != null) {
                        times.add(new TimeOption(raw.trim(), time));
                    } else {
                        throw new IllegalStateException("Некорректное время в файле контента: " + raw);
                    }
                }
            }
        }
        return new CacheParts(content, Set.copyOf(excludedDates), Set.copyOf(excludedWeekdays), maxAdvanceDays, List.copyOf(times));
    }

    private static LocalDate parseDate(String raw, Source source) {
        try {
            return LocalDate.parse(raw.trim());
        } catch (DateTimeParseException | NullPointerException e) {
            throw new IllegalStateException("Некорректная дата в файле контента " + source.path() + ": " + raw
                    + ". Ожидается формат ГГГГ-ММ-ДД");
        }
    }

    private static LocalTime parseTime(String raw) {
        try {
            return LocalTime.parse(raw);
        } catch (DateTimeParseException | NullPointerException e) {
            return null;
        }
    }

    public record TimeOption(String label, LocalTime time) {
    }

    private record Source(Path path, String stamp) {
    }

    private record CacheParts(Content content,
                              Set<LocalDate> excludedDates,
                              Set<Integer> excludedWeekdays,
                              int maxAdvanceDays,
                              List<TimeOption> times) {
    }

    private record Cache(Source source, CacheParts parts) {

        Content content() {
            return parts.content();
        }

        Set<LocalDate> excludedDates() {
            return parts.excludedDates();
        }

        Set<Integer> excludedWeekdays() {
            return parts.excludedWeekdays();
        }

        int maxAdvanceDays() {
            return parts.maxAdvanceDays();
        }

        List<TimeOption> times() {
            return parts.times();
        }
    }
}
