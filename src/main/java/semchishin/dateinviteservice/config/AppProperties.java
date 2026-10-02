package semchishin.dateinviteservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AppProperties(Admin admin, String contentFile, String timezone) {

    public record Admin(String username, String password) {

        public String usernameOrDefault() {
            return username == null || username.isBlank() ? "admin" : username;
        }

        public String encodedPasswordOrDefault() {
            String value = password == null || password.isBlank() ? "changeme" : password.trim();
            return value.startsWith("{") ? value : "{noop}" + value;
        }
    }

    public String contentFileOrDefault() {
        return contentFile == null || contentFile.isBlank() ? "data/content.json" : contentFile;
    }

    public String timezoneOrDefault() {
        return timezone == null || timezone.isBlank() ? "Europe/Moscow" : timezone;
    }
}
