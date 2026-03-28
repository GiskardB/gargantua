package ai.gargantua.example;

import ai.gargantua.example.tools.SearchTool;
import ai.gargantua.example.tools.WeatherTool;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.context.annotation.ImportRuntimeHints;
import org.springframework.stereotype.Component;

@Component
@ImportRuntimeHints(AgentKitRuntimeHints.Registrar.class)
public class AgentKitRuntimeHints {

    static class Registrar implements RuntimeHintsRegistrar {

        @Override
        public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
            // Register tool classes and their inner record types for reflection
            registerToolClass(hints, WeatherTool.class);
            registerToolClass(hints, WeatherTool.WeatherResult.class);
            registerToolClass(hints, WeatherTool.ForecastDay.class);
            registerToolClass(hints, WeatherTool.ForecastResult.class);
            registerToolClass(hints, WeatherTool.NotificationResult.class);
            registerToolClass(hints, SearchTool.class);
            registerToolClass(hints, SearchTool.SearchResultItem.class);
            registerToolClass(hints, SearchTool.SearchResult.class);

            // Register resource patterns for skills
            hints.resources().registerPattern("skills/**");
            hints.resources().registerPattern("static/**");
        }

        private void registerToolClass(RuntimeHints hints, Class<?> clazz) {
            hints.reflection().registerType(clazz,
                    MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                    MemberCategory.INVOKE_DECLARED_METHODS,
                    MemberCategory.INVOKE_PUBLIC_METHODS,
                    MemberCategory.DECLARED_FIELDS,
                    MemberCategory.PUBLIC_FIELDS);
        }
    }
}
