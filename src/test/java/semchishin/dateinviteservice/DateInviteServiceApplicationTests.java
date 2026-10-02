package semchishin.dateinviteservice;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest(properties = {
        "app.content-file=src/test/resources/test-content.json",
        "app.admin.username=admin",
        "app.admin.password=secret",
        "spring.docker.compose.enabled=false"
})
@Import({TestcontainersConfiguration.class, FixedClockConfiguration.class})
class DateInviteServiceApplicationTests {

    @Test
    void contextLoads() {
    }

}
