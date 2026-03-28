package ai.gargantua.core.tool;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface CacheableToolResult {

    int ttlSeconds() default 300;

    String[] keyParams() default {};

    CacheScope scope() default CacheScope.GLOBAL;
}
