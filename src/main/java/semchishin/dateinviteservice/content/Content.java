package semchishin.dateinviteservice.content;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public record Content(Texts texts, List<Place> places, Schedule schedule) {

    public List<Place> placesOrEmpty() {
        return places == null ? List.of() : places;
    }

    public Optional<Place> placeById(String id) {
        return placesOrEmpty().stream()
                .filter(place -> place.id().equals(id))
                .findFirst();
    }

    public record Texts(String invitee,
                        String errorWrongAnswer,
                        String back,
                        String edit,
                        String confirm,
                        String waiting,
                        Map<String, StepText> steps,
                        CustomPlace customPlace,
                        FinaleText finale,
                        Sign sign) {

        public StepText step(String key) {
            StepText text = steps == null ? null : steps.get(key);
            return text == null ? new StepText("", "", "", "") : text;
        }

        public CustomPlace customPlaceOrDefault() {
            return customPlace == null ? new CustomPlace("Свой вариант", "Напиши свой вариант") : customPlace;
        }

        public Sign signOrDefault() {
            return sign == null ? new Sign("", "") : sign;
        }
    }

    public record Sign(String thanks, String author) {
    }

    public record CustomPlace(String label, String placeholder) {
    }

    public record StepText(String title, String subtitle, String yes, String no) {
    }

    public record FinaleText(String title, String message, String placeLabel, String dateLabel, String timeLabel) {
    }

    public record Place(String id, String title, String description, String image) {
    }

    public record Schedule(List<String> excludedDates,
                           List<Integer> excludedWeekdays,
                           Integer maxAdvanceDays,
                           List<String> times) {
    }
}
