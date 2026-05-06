package ai.gargantua.example.weather;

import ai.gargantua.example.weather.tools.WeatherTool;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Smoke + bean-presence checks for the weather agent. Boots the full Spring
 * context using the {@code embedded} profile so MongoDB / Redis aren't needed.
 *
 * <p>The {@code WeatherTool} actually hits a real public API (Open-Meteo).
 * That call is exercised separately in unit-style tests that you can add when
 * working offline; the suite below only verifies the wiring.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("embedded")
class WeatherApplicationTest {

    @Autowired
    private ApplicationContext ctx;

    @Test
    void contextLoads() {
        assertNotNull(ctx);
    }

    @Test
    void weatherToolBeanIsRegistered() {
        assertNotNull(ctx.getBean(WeatherTool.class));
    }
}
