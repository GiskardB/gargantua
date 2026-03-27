package io.agentkit.core.guardrail;

public interface InputGuardrail {

    String name();

    boolean isEnabled(Object props);

    GuardrailResult check(GuardrailInputContext ctx);
}
