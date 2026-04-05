package ai.gargantua.eval.plugin;

import ai.gargantua.eval.LlmJudge;

/**
 * Delegates to LlmJudge for LLM-based scoring.
 * Config keys: judge.endpoint, judge.model, judge.key
 */
public class LlmJudgePlugin implements EvalPlugin {

    @Override
    public String name() {
        return "llm-judge";
    }

    @Override
    public String description() {
        return "LLM-as-Judge scoring via OpenAI-compatible API (OpenAI, Ollama, LiteLLM)";
    }

    @Override
    public PluginResult evaluate(PluginContext context) {
        var cfg = context.config();
        var endpoint = cfg.getOrDefault("judge.endpoint", "http://localhost:11434/v1");
        var model = cfg.getOrDefault("judge.model", "phi4-mini");
        var key = cfg.getOrDefault("judge.key", "");

        var judge = new LlmJudge(endpoint, key, model);
        var result = judge.judge(context.evalCase(), context.agentResponse());

        var verdict = result.score() >= 0.85 ? "PASS"
                : result.score() <= 0.3 ? "FAIL" : "PARTIAL";

        return new PluginResult(result.score(), verdict, result.reason(),
                result.passed(), result.failed());
    }
}
