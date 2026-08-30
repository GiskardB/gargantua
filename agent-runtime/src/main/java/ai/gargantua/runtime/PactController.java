package ai.gargantua.runtime;

import ai.gargantua.bundle.LoadedBundle;
import ai.gargantua.core.pact.PactManifest;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Serves this agent's manifest as a standalone PACT v1 Core document — the live
 * counterpart to {@code /.well-known/agent.json} (A2A). Only meaningful in Runtime mode:
 * a Library-mode app has no {@code gargantua.ai/v1} manifest to project from, which is
 * why this lives here rather than in {@code agent-engine} alongside the A2A controller
 * (agent-engine has no dependency on {@code agent-bundle}/{@link LoadedBundle} by design).
 *
 * @see PactManifest
 */
@RestController
public class PactController {

    private final LoadedBundle bundle;

    public PactController(LoadedBundle bundle) {
        this.bundle = bundle;
    }

    @GetMapping(value = "/.well-known/pact.json", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> wellKnownPactJson() {
        PactManifest pact = PactManifest.from(bundle.manifest());
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(60, TimeUnit.SECONDS))
                .body(pact.toWireMap());
    }
}
