package ai.gargantua.autoconfigure;

import org.springframework.beans.factory.ObjectProvider;

import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * Singleton {@link ObjectProvider} that always reports "no bean available".
 * Used by test-only constructors and convenience entrypoints to avoid pulling
 * Mockito just for an empty provider.
 */
final class EmptyObjectProvider<T> implements ObjectProvider<T> {

    private static final EmptyObjectProvider<Object> INSTANCE = new EmptyObjectProvider<>();

    @SuppressWarnings("unchecked")
    static <T> ObjectProvider<T> instance() {
        return (ObjectProvider<T>) INSTANCE;
    }

    private EmptyObjectProvider() {}

    @Override
    public T getObject() {
        throw new IllegalStateException("No bean available");
    }

    @Override
    public T getObject(Object... args) {
        throw new IllegalStateException("No bean available");
    }

    @Override
    public T getIfAvailable() {
        return null;
    }

    @Override
    public T getIfUnique() {
        return null;
    }

    @Override
    public void ifAvailable(Consumer<T> dependencyConsumer) {
        // no-op
    }

    @Override
    public void ifUnique(Consumer<T> dependencyConsumer) {
        // no-op
    }

    @Override
    public Stream<T> stream() {
        return Stream.empty();
    }

    @Override
    public Stream<T> orderedStream() {
        return Stream.empty();
    }
}
