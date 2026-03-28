package ai.gargantua.core.guardrail;

public interface OutputGuardrail {

    String name();

    boolean isEnabled(Object props);

    GuardrailOutputResult process(GuardrailOutputContext ctx);
}
