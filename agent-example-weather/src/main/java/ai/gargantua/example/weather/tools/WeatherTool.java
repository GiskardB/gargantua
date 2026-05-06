package ai.gargantua.example.weather.tools;

import ai.gargantua.core.tool.AgentTool;
import ai.gargantua.core.tool.CacheScope;
import ai.gargantua.core.tool.CacheableToolResult;
import ai.gargantua.core.tool.ToolRetry;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Weather tools backed by <a href="https://open-meteo.com">Open-Meteo</a> — a
 * free public weather API that does not require an API key. Two methods:
 *
 * <ul>
 *   <li>{@link #getCurrentWeather(String)}  — current temperature + wind for a city</li>
 *   <li>{@link #getForecast(String, int)}   — daily min/max forecast for the next N days</li>
 * </ul>
 *
 * <p>Both methods use Open-Meteo's geocoding endpoint to resolve a city name to
 * coordinates, then hit the forecast endpoint. Results are cached
 * ({@code GLOBAL} scope, short TTL) because the same city/forecast pair gets
 * hit by every user. Network calls are retried twice on {@link IOException}.</p>
 */
@Component
public class WeatherTool {

    private static final String GEOCODE_URL  = "https://geocoding-api.open-meteo.com/v1/search";
    private static final String FORECAST_URL = "https://api.open-meteo.com/v1/forecast";

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    private final ObjectMapper mapper = new ObjectMapper();

    /** Current weather for a city. */
    public record CurrentWeather(
            String city, double latitude, double longitude,
            double temperatureC, double windSpeedKmh, String description
    ) {}

    /** A single day in the forecast. */
    public record ForecastDay(String date, double minTempC, double maxTempC, String description) {}

    /** Multi-day forecast for a city. */
    public record Forecast(String city, double latitude, double longitude, java.util.List<ForecastDay> days) {}

    @AgentTool(description = """
            Returns the current temperature (°C), wind speed (km/h) and a short
            weather description for the given city. Use whenever the user asks
            "what's the weather in X" or "how warm is it in Y".
            """)
    @CacheableToolResult(ttlSeconds = 300, keyParams = {"city"}, scope = CacheScope.GLOBAL)
    @ToolRetry(maxAttempts = 2, waitDurationMs = 300, retryOn = {IOException.class})
    public CurrentWeather getCurrentWeather(String city) throws IOException, InterruptedException {
        var coords = geocode(city);
        String url = "%s?latitude=%s&longitude=%s&current_weather=true".formatted(
                FORECAST_URL, coords.latitude, coords.longitude);
        JsonNode json = httpGetJson(url);
        JsonNode cw = json.path("current_weather");
        return new CurrentWeather(
                coords.name,
                coords.latitude,
                coords.longitude,
                cw.path("temperature").asDouble(),
                cw.path("windspeed").asDouble(),
                describeWmo(cw.path("weathercode").asInt())
        );
    }

    @AgentTool(description = """
            Returns a daily min/max temperature forecast for the next N days
            (1–10) for the given city. Use when the user asks for tomorrow's
            weather, the weekend forecast, "the next 5 days in Rome", etc.
            """)
    @CacheableToolResult(ttlSeconds = 1800, keyParams = {"city", "days"}, scope = CacheScope.GLOBAL)
    @ToolRetry(maxAttempts = 2, waitDurationMs = 300, retryOn = {IOException.class})
    public Forecast getForecast(String city, int days) throws IOException, InterruptedException {
        int safeDays = Math.max(1, Math.min(days, 10));
        var coords = geocode(city);
        String url = ("%s?latitude=%s&longitude=%s" +
                "&daily=temperature_2m_min,temperature_2m_max,weathercode" +
                "&timezone=auto&forecast_days=%d")
                .formatted(FORECAST_URL, coords.latitude, coords.longitude, safeDays);
        JsonNode json = httpGetJson(url);
        JsonNode daily = json.path("daily");
        var dates = daily.path("time");
        var mins  = daily.path("temperature_2m_min");
        var maxs  = daily.path("temperature_2m_max");
        var codes = daily.path("weathercode");
        var out = new java.util.ArrayList<ForecastDay>(safeDays);
        for (int i = 0; i < dates.size(); i++) {
            out.add(new ForecastDay(
                    dates.get(i).asText(),
                    mins.get(i).asDouble(),
                    maxs.get(i).asDouble(),
                    describeWmo(codes.get(i).asInt())
            ));
        }
        return new Forecast(coords.name, coords.latitude, coords.longitude, out);
    }

    // ── internal helpers ────────────────────────────────────────────

    private record Coords(String name, double latitude, double longitude) {}

    private Coords geocode(String city) throws IOException, InterruptedException {
        if (city == null || city.isBlank()) {
            throw new IllegalArgumentException("city must not be blank");
        }
        String url = "%s?name=%s&count=1&format=json".formatted(
                GEOCODE_URL, URLEncoder.encode(city.trim(), StandardCharsets.UTF_8));
        JsonNode json = httpGetJson(url);
        JsonNode results = json.path("results");
        if (!results.isArray() || results.isEmpty()) {
            throw new IllegalStateException("city not found: " + city);
        }
        JsonNode top = results.get(0);
        return new Coords(
                top.path("name").asText(city),
                top.path("latitude").asDouble(),
                top.path("longitude").asDouble()
        );
    }

    private JsonNode httpGetJson(String url) throws IOException, InterruptedException {
        var req = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(8))
                .GET()
                .build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() / 100 != 2) {
            throw new IOException("HTTP " + resp.statusCode() + " for " + url);
        }
        return mapper.readTree(resp.body());
    }

    /** Translate Open-Meteo WMO weather codes into a short human description. */
    private static String describeWmo(int code) {
        return switch (code) {
            case 0 -> "clear sky";
            case 1, 2 -> "mainly clear";
            case 3 -> "overcast";
            case 45, 48 -> "fog";
            case 51, 53, 55 -> "drizzle";
            case 56, 57 -> "freezing drizzle";
            case 61, 63, 65 -> "rain";
            case 66, 67 -> "freezing rain";
            case 71, 73, 75 -> "snow";
            case 77 -> "snow grains";
            case 80, 81, 82 -> "rain showers";
            case 85, 86 -> "snow showers";
            case 95 -> "thunderstorm";
            case 96, 99 -> "thunderstorm with hail";
            default -> "unknown (WMO " + code + ")";
        };
    }
}
