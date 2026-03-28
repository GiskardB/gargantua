package ai.gargantua.core.tool;

import java.io.IOException;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ToolRetry {

    int maxAttempts() default 3;

    long waitDurationMs() default 500;

    double backoffMultiplier() default 2.0;

    long maxWaitDurationMs() default 5000;

    Class<? extends Throwable>[] retryOn() default {IOException.class};

    Class<? extends Throwable>[] abortOn() default {IllegalArgumentException.class};
}
