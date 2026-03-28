package ai.gargantua.core.guardrail;

public interface InputGuardrail {

    String name();

    boolean isEnabled(Object props);

    GuardrailResult check(GuardrailInputContext ctx);
}
