package ai.gargantua.example.tools;

import ai.gargantua.core.tool.AgentTool;
import ai.gargantua.core.tool.CacheScope;
import ai.gargantua.core.tool.CacheableToolResult;
import ai.gargantua.core.tool.RequiresApproval;
import ai.gargantua.core.tool.ToolRetry;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.IntStream;

@Component
public class WeatherTool {

    public record WeatherResult(
            String city,
            double temperatureCelsius,
            String condition,
            int humidityPercent,
            double windSpeedKmh,
            String timestamp
    ) {}

    public record ForecastDay(
            String date,
            double highCelsius,
            double lowCelsius,
            String condition,
            int precipitationPercent
    ) {}

    public record ForecastResult(
            String city,
            List<ForecastDay> days
    ) {}

    public record NotificationResult(
            boolean sent,
            String alertType,
            String message,
            String timestamp
    ) {}

    @AgentTool(description = "Get the current weather for a given city")
    @CacheableToolResult(ttlSeconds = 300, keyParams = {"city"}, scope = CacheScope.GLOBAL)
    public WeatherResult getWeather(String city) {
        // Mock implementation returning hardcoded weather data
        return new WeatherResult(
                city,
                22.5,
                "Partly Cloudy",
                65,
                12.3,
                java.time.Instant.now().toString()
        );
    }

    @AgentTool(description = "Get a multi-day weather forecast for a given city")
    @ToolRetry(maxAttempts = 3)
    public ForecastResult getForecast(String city, int days) {
        if (days < 1 || days > 5) {
            throw new IllegalArgumentException("days must be between 1 and 5");
        }
        // Mock implementation returning hardcoded forecast data
        LocalDate today = LocalDate.now();
        List<ForecastDay> forecastDays = IntStream.range(0, days)
                .mapToObj(i -> new ForecastDay(
                        today.plusDays(i + 1).toString(),
                        25.0 - i,
                        15.0 - i,
                        i % 2 == 0 ? "Sunny" : "Rainy",
                        i % 2 == 0 ? 10 : 70
                ))
                .toList();
        return new ForecastResult(city, forecastDays);
    }

    @AgentTool(description = "Send a weather alert notification to subscribed users")
    @RequiresApproval(message = "Send weather alert", showParameters = {"alertType", "message"})
    public NotificationResult sendWeatherAlert(String alertType, String message) {
        // Mock implementation - in production this would send real notifications
        return new NotificationResult(
                true,
                alertType,
                message,
                java.time.Instant.now().toString()
        );
    }
}
