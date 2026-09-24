# Proposta: classificazione skill offline in-process, pluggable

> Stato: **proposta approvata per implementazione** (v2 — vedi §12 per cosa è
> cambiato rispetto alla prima bozza). Nomi di classi/pacchetti sono allineati
> al codice esistente in `agent-core` / `agent-engine`; dove non esistono
> ancora sono marcati **[NUOVO]**.

## 1. Perché cambiare

Oggi (v1.4.0-SNAPSHOT) il routing delle skill è **Hybrid**:

```
SemanticRoutingService.route()
  → embedding ONNX in-process (AllMiniLmL6V2QuantizedEmbeddingModel, ~2-5ms)
  → cosine similarity vs skill.description(), hardcoded inline nel service
  → se score >= agent.routing.semantic.threshold (default 0.6): FATTO
  → altrimenti (strategy=hybrid, default): RoutingService.routeWithLlm()
      → chiama agent.llm.routing-model.* (LLM_ROUTING_PROVIDER/MODEL/ENDPOINT/API_KEY,
        con fallback automatico su LLM_PRIMARY_* da eeb8950)
```

Il problema non è la *presenza* di un fallback LLM configurabile — quello
resta utile e viene **mantenuto** (vedi §9). Il problema è che il solo
meccanismo di match locale disponibile oggi è la cosine similarity su
embedding, cablata dentro `SemanticRoutingService` senza un punto di
estensione: non è possibile sostituirla con un classificatore supervisionato
addestrato sulle skill (più accurato su frasi ambigue) senza riscrivere il
service. Questa proposta rende quel primo livello di match **pluggable**
dietro un'interfaccia, lasciando invariato tutto il resto della catena.

## 2. Cosa resta fuori scope

`LlmRouter` / `RoutingRuleEvaluator` / `agent.llm.routing-rules` **non sono
toccati**: selezionano *quale alias LLM* (`primary`/`fallback`/alias custom)
esegue la skill già scelta, per orari/attributi/percentuali (vedi
`docs/llm-configuration.md`) — un concetto diverso dalla scelta della skill.

`RoutingService` / `agent.llm.routing-model.*` / le env `LLM_ROUTING_*`
**non vengono eliminati**: restano il meccanismo di fallback quando il
classificatore locale non è sicuro (vedi §9). Questa è una correzione
rispetto alla bozza iniziale, che ne proponeva la rimozione totale —
mantenerli dà a chi opera il framework la flessibilità di puntare il
fallback su un modello dedicato (es. locale/economico), diverso dal primario,
senza perdere il comportamento zero-config attuale quando `LLM_ROUTING_*`
non è impostato.

## 3. Nuovo flusso

```
User Request
    │
    ▼
SkillRegistry.listMeta()             (invariato)
    │
    ▼
SkillClassifier.classify()  [NUOVO]  ── engine pluggabile: semantic | onnx | tribuo
    │
    ▼
confidence >= agent.routing.classifier.threshold ?
    │yes                    │no
    ▼                       ▼
Skill Selection      RoutingService.routeWithLlm()   (INVARIATO)
    │                 → agent.llm.routing-model.* (LLM_ROUTING_* se
    │                   esplicitato, altrimenti eredita da LLM_PRIMARY_*)
    ▼                       │
Primary LLM  ◄──────────────┘
    │
    ▼
Tool calling + Response
```

`X-Force-Skill` e `RoutingResult.forced()` restano invariati (bypass totale).
L'unica differenza rispetto al flusso Hybrid odierno è **cosa** decide il
primo livello (interfaccia pluggabile invece di cosine similarity cablata) —
il secondo livello (LLM fallback) è lo stesso codice, stessa configurazione,
stesso comportamento zero-config di oggi.

## 4. Interfaccia `SkillClassifier` [NUOVO]

Astrae il primo livello di match, oggi cablato dentro
`SemanticRoutingService`. Vive in `agent-core` (nessuna dipendenza Spring,
come `SkillRegistry`):

```java
package ai.gargantua.core.routing;

import ai.gargantua.core.skill.SkillMeta;
import java.util.List;

/**
 * Local, in-process classifier that maps a user message to a skill name.
 * Implementations must not perform network I/O — inference runs embedded.
 */
public interface SkillClassifier {

    /** (Re)builds any internal index/state from the current skill set. Called at boot and on reload. */
    void index(List<SkillMeta> skills);

    /** Classifies a message against the indexed skills. */
    ClassificationResult classify(String userMessage, List<SkillMeta> skills);

    /** Engine identifier for logging, e.g. "semantic", "onnx", "tribuo". */
    String engine();
}
```

```java
package ai.gargantua.core.routing;

import java.util.List;

public record ClassificationResult(
        String skillName,
        double confidence,
        List<ScoredSkill> candidates   // top-K, per debug/log — può essere vuota
) {
    public static ClassificationResult of(String skillName, double confidence) {
        return new ClassificationResult(skillName, confidence, List.of());
    }

    public static final ClassificationResult NONE = new ClassificationResult(null, 0.0, List.of());
}

public record ScoredSkill(String skillName, double score) {}
```

### `RoutingResult` / `RoutingMethod` — **invariati**

Niente rename: `RoutingMethod.SEMANTIC` continua a esistere (solo il
Javadoc viene aggiornato per chiarire che ora indica "matched dal
`SkillClassifier` pluggable configurato", non necessariamente embedding
cosine similarity), `RoutingMethod.LLM` continua a indicare il fallback via
`RoutingService`, `RoutingResult.semantic(...)`/`.llm(...)`/`.forced(...)`
restano le stesse factory. Zero impatto sui chiamanti esistenti
(`ChatController`, `ChatStreamController`, admin endpoints, test) che
consumano `RoutingResult`.

### Orchestratore — `SemanticRoutingService` [MODIFICATO, non rinominato]

Stesso nome pubblico (usato da `AgentAutoConfiguration`,
`DefaultOrchestratorEngine`, `ChatController`/`ChatStreamController`): cambia
solo l'implementazione interna, che ora delega al `SkillClassifier` iniettato
invece di istanziare direttamente `AllMiniLmL6V2QuantizedEmbeddingModel`.

```java
public class SemanticRoutingService {

    private final AgentProperties properties;
    private final RoutingService routingService;      // invariato
    private final SkillClassifier classifier;          // NUOVO — iniettato

    public SemanticRoutingService(AgentProperties properties, RoutingService routingService,
                                   SkillClassifier classifier) {
        this.properties = properties;
        this.routingService = routingService;
        this.classifier = classifier;
    }

    public void index(List<SkillMeta> skills) {
        classifier.index(skills);
    }

    public RoutingResult route(String userMessage, List<SkillMeta> skills) {
        if (skills == null || skills.isEmpty()) {
            return RoutingResult.semantic(properties.getRouting().getFallbackSkill(), 0.0);
        }
        String strategy = normalizeStrategy(properties.getRouting().getStrategy());
        if ("llm".equals(strategy)) {
            return RoutingResult.llm(routingService.routeWithLlm(userMessage, skills));
        }

        ClassificationResult result = classifier.classify(userMessage, skills);
        double threshold = properties.getRouting().getClassifier().getThreshold();

        if (result.skillName() != null && result.confidence() >= threshold) {
            return RoutingResult.semantic(result.skillName(), result.confidence());
        }
        if ("semantic".equals(strategy)) {
            return RoutingResult.semantic(properties.getRouting().getFallbackSkill(), Math.max(0.0, result.confidence()));
        }
        // hybrid (default) — INVARIATO: stesso fallback LLM di oggi
        return RoutingResult.llm(routingService.routeWithLlm(userMessage, skills));
    }
}
```

`agent.routing.strategy` (`semantic|llm|hybrid`) resta com'è — non introduce
alcuna rottura concettuale, solo il primo ramo ("semantic") ora esegue un
engine pluggabile invece di cosine similarity fissa.

## 5. Le implementazioni di `SkillClassifier`

### 5.1 `SemanticSimilarityClassifier` — refactor 1:1 dell'esistente

È il codice già in `SemanticRoutingService` oggi (embedding
`AllMiniLmL6V2QuantizedEmbeddingModel` + cosine similarity), spostato dietro
l'interfaccia senza cambiarne il comportamento. **Zero nuove dipendenze** —
`langchain4j-embeddings-all-minilm-l6-v2-q` è già nel `pom.xml`. Resta
l'engine di **default**: nessuna fase di training richiesta, funziona subito
sulla sola `description`.

```java
public class SemanticSimilarityClassifier implements SkillClassifier {
    private final EmbeddingModel embeddingModel = new AllMiniLmL6V2QuantizedEmbeddingModel();
    private final Map<String, Embedding> index = new ConcurrentHashMap<>();

    @Override public void index(List<SkillMeta> skills) { /* identico a SemanticRoutingService.index() oggi */ }

    @Override public ClassificationResult classify(String userMessage, List<SkillMeta> skills) {
        // stessa cosine similarity di oggi, wrappata in ClassificationResult
    }

    @Override public String engine() { return "semantic"; }
}
```

### 5.2 `TribuoClassifier` — motore raccomandato per il training

Usa **Tribuo** (`org.tribuo:tribuo-classification-sgd`, pure Java, nessuna
libreria nativa) come *testa di classificazione* addestrata, con
`AllMiniLmL6V2QuantizedEmbeddingModel` (già presente) come **feature
extractor**. Il modello addestrato (vedi §8) viene serializzato in
**protobuf** (`Model.serializeToFile`/`Model.deserializeFromFile`, non Java
serialization — meno reflection, migliore per un futuro native-image, vedi §11).

```java
public class TribuoClassifier implements SkillClassifier {
    private final EmbeddingModel featureExtractor = new AllMiniLmL6V2QuantizedEmbeddingModel();
    private final Model<Label> model;   // Model.deserializeFromFile(modelPath)

    @Override public void index(List<SkillMeta> skills) { /* no-op: il modello è già addestrato offline */ }

    @Override public ClassificationResult classify(String userMessage, List<SkillMeta> skills) {
        float[] vector = featureExtractor.embed(userMessage).content().vector();
        ArrayExample<Label> example = toExample(vector);          // Label placeholder, non usato in predizione
        Prediction<Label> pred = model.predict(example);
        return toClassificationResult(pred);                       // getOutput()/getOutputScores()
    }

    @Override public String engine() { return "tribuo"; }
}
```

### 5.3 `OnnxClassifier` — escape hatch per teste di classificazione custom

Per chi vuole portare una testa di classificazione già addestrata altrove
(es. sklearn/PyTorch esportato in ONNX) che prende in input lo stesso vettore
di embedding MiniLM (384-dim, coerente con §5.1/§5.2) e produce logit per
skill. Usa `ai.onnxruntime:onnxruntime` **direttamente** — già dipendenza
transitiva di `langchain4j-embeddings-all-minilm-l6-v2-q`, resa esplicita nel
`pom.xml` per pin di versione. Nessun tokenizer richiesto: l'input è già il
vettore prodotto dallo stesso `AllMiniLmL6V2QuantizedEmbeddingModel`, non
testo grezzo — questo evita di dover portare in classpath una libreria di
tokenizzazione HuggingFace solo per questo escape hatch.

```java
public class OnnxClassifier implements SkillClassifier {
    private final EmbeddingModel featureExtractor = new AllMiniLmL6V2QuantizedEmbeddingModel();
    private final OrtEnvironment env = OrtEnvironment.getEnvironment();
    private final OrtSession session;   // caricata da agent.routing.classifier.onnx.model-path
    private final List<String> labels;  // ordine coerente con l'output layer del modello

    @Override public ClassificationResult classify(String userMessage, List<SkillMeta> skills) {
        float[] vector = featureExtractor.embed(userMessage).content().vector();
        try (OnnxTensor input = OnnxTensor.createTensor(env, new float[][]{vector});
             OrtSession.Result out = session.run(Map.of("embedding", input))) {
            float[] logits = ((float[][]) out.get(0).getValue())[0];
            return softmaxToResult(logits, labels);
        }
    }

    @Override public String engine() { return "onnx"; }
}
```

## 6. Configurazione Spring Boot

### 6.1 `AgentProperties` — sostituisce solo `Routing.Semantic`

```java
public static class Routing {
    private String strategy = "hybrid";     // semantic | llm | hybrid — INVARIATO
    private String fallbackSkill = "default";
    private Classifier classifier = new Classifier();

    public static class Classifier {
        private String engine = "semantic";     // semantic | onnx | tribuo | <bean name custom>
        private double threshold = 0.6;
        private Semantic semantic = new Semantic();
        private Onnx onnx = new Onnx();
        private Tribuo tribuo = new Tribuo();

        public static class Semantic { private String model = "all-MiniLM-L6-v2"; }
        public static class Onnx     { private String modelPath = "classpath:models/skill-classifier.onnx"; }
        public static class Tribuo   { private String modelPath = "classpath:models/skill-classifier.tribuo"; }
    }
}
```

`Llm.RoutingModel` **non cambia** — resta esattamente come oggi in
`AgentProperties.Llm`, usato solo da `RoutingService`.

### 6.2 `application.yml`

```yaml
agent:
  llm:
    primary:
      provider: ${LLM_PRIMARY_PROVIDER:openai}
      model: ${LLM_PRIMARY_MODEL:gpt-4o}
      api-key: ${LLM_PRIMARY_API_KEY:}
      endpoint: ${LLM_PRIMARY_ENDPOINT:https://api.openai.com/v1}
    routing-model:                     # INVARIATO
      provider: ${LLM_ROUTING_PROVIDER:${LLM_PRIMARY_PROVIDER:openai}}
      model: ${LLM_ROUTING_MODEL:${LLM_PRIMARY_MODEL:gpt-4o}}
      endpoint: ${LLM_ROUTING_ENDPOINT:${LLM_PRIMARY_ENDPOINT:https://api.openai.com/v1}}
      api-key: ${LLM_ROUTING_API_KEY:${LLM_PRIMARY_API_KEY:}}
  routing:
    strategy: hybrid
    fallback-skill: default
    classifier:
      engine: semantic        # nessun training richiesto per iniziare
      threshold: 0.6
      # engine: tribuo        # dopo `mvn gargantua:train-router`
      # tribuo:
      #   model-path: classpath:models/skill-classifier.tribuo
```

Unico rename rispetto a oggi: `agent.routing.semantic.threshold` →
`agent.routing.classifier.threshold`, `agent.routing.semantic.model` →
`agent.routing.classifier.semantic.model` (serve a fare spazio alle
sotto-sezioni `onnx`/`tribuo`). Tutto il resto — inclusa l'intera sezione
`agent.llm.routing-model` e le env `LLM_ROUTING_*` — è testualmente identico
a oggi.

### 6.3 Autoconfigurazione a scelta di engine

```java
@Configuration
@EnableConfigurationProperties(AgentProperties.class)
public class SemanticRoutingAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(SkillClassifier.class)   // un @Bean SkillClassifier custom dell'utente vince sempre
    @ConditionalOnProperty(name = "agent.routing.classifier.engine", havingValue = "tribuo")
    SkillClassifier tribuoClassifier(AgentProperties p, ResourceLoader loader) { /* ... */ }

    @Bean
    @ConditionalOnMissingBean(SkillClassifier.class)
    @ConditionalOnProperty(name = "agent.routing.classifier.engine", havingValue = "onnx")
    SkillClassifier onnxClassifier(AgentProperties p, ResourceLoader loader) { /* ... */ }

    @Bean
    @ConditionalOnMissingBean(SkillClassifier.class)
    @ConditionalOnProperty(name = "agent.routing.classifier.engine", havingValue = "semantic", matchIfMissing = true)
    SkillClassifier semanticClassifier() { return new SemanticSimilarityClassifier(); }

    @Bean
    SemanticRoutingService semanticRoutingService(AgentProperties properties, RoutingService routingService,
                                                    SkillClassifier classifier) {
        return new SemanticRoutingService(properties, routingService, classifier);
    }
}
```

## 7. Compatibilità con `SkillRegistry` / SKILL.md

Nessuna modifica a `SkillRegistry`, `SkillMeta`, `SkillCard`: il classifier
riceve `List<SkillMeta>` esattamente come oggi. Aggiunto **un solo campo
opzionale** al frontmatter, usato solo in fase di training (non a runtime —
stesso trattamento già riservato a `examples`):

| Campo | Tipo | Richiesto | Descrizione |
|---|---|---|---|
| `metadata.routing-hints` | list of strings | No | Keyword/frasi brevi come esempi di training aggiuntivi a bassa ambiguità (es. `spese`, `budget`). Solo per `mvn gargantua:train-router`; non letto a runtime. |

`examples` (già esistente, oggi "Not used at runtime by the router") viene
riusato così com'è dalla pipeline di training come frasi positive.

```yaml
---
name: spending-analysis
description: Analizza le spese personali per categoria e periodo.
version: 1.0.0
allowed-tools: [getTransactions, getCategorySummary]
examples:
  - quanto ho speso questo mese
  - mostrami le categorie di spesa
  - confronta agosto con luglio
metadata:
  active: true
  domain: finance
  routing-hints: [spese, categorie, budget]
---
```

## 8. Training automatico — `mvn gargantua:train-router`

Nuovo modulo Maven `agent-router-trainer-maven-plugin`, stesso scheletro di
`agent-skill-linter-maven-plugin` (`packaging=maven-plugin`, `AbstractMojo`,
scansione filesystem `SKILL.md`). Invocazione allineata a come è già
consumato il linter in questo repo: coordinate plugin complete legate a una
fase (`process-resources`), non uno shorthand `gargantua:` builtin (Maven
richiederebbe una entry in `~/.m2/settings.xml` → `<pluginGroups>` per
quello, cosa che ogni consumer può fare da sé — non è responsabilità del
plugin).

```java
@Mojo(name = "train-router", defaultPhase = LifecyclePhase.PROCESS_RESOURCES,
      requiresDependencyResolution = ResolutionScope.COMPILE)
public class TrainRouterMojo extends AbstractMojo {
    @Parameter(defaultValue = "${project.basedir}/src/main/resources/skills")
    private File skillsDirectory;

    @Parameter(defaultValue = "${project.build.outputDirectory}/models/skill-classifier.tribuo")
    private File outputPath;

    @Override public void execute() { /* vedi pipeline sotto */ }
}
```

### 8.1 Pipeline

```
1. Scan     → cammina skillsDirectory, legge frontmatter YAML di ogni SKILL.md
2. Dataset  → per ogni skill attiva: positivi = examples[] + routing-hints[] + [description], label = skill.name()
3. Feature  → AllMiniLmL6V2QuantizedEmbeddingModel.embed(text) per ogni esempio (stessa dipendenza già nel pom)
4. Train    → Tribuo LogisticRegressionTrainer su (embedding, label)
5. Export   → Model.serializeToFile(outputPath) — protobuf, non Java serialization
6. Package  → outputPath è già sotto target/classes → finisce nel JAR come risorsa
```

### 8.2 Fail-fast utile

Se una skill ha meno di 3 frasi totali (examples + routing-hints +
description), il Mojo fallisce la build nominando la skill: un
classificatore supervisionato con una frase per classe è un dataset-fantasma,
meglio bloccare al build time.

## 9. Confidence e fallback — invariato rispetto a oggi

`agent.routing.classifier.threshold` (default 0.6, stesso significato di
`agent.routing.semantic.threshold` oggi) resta un doppio 0.0-1.0 confrontato
col confidence migliore restituito da `SkillClassifier.classify()`.

**Sotto soglia, nulla cambia rispetto al comportamento Hybrid attuale**:
`RoutingService.routeWithLlm(userMessage, skills)` viene chiamato esattamente
come oggi, con lo stesso modello configurato in `agent.llm.routing-model.*`:

- se `LLM_ROUTING_PROVIDER`/`LLM_ROUTING_MODEL`/`LLM_ROUTING_ENDPOINT`/
  `LLM_ROUTING_API_KEY` sono impostate, il fallback usa quel modello dedicato
  (utile per puntare la disambiguazione a un modello locale/economico,
  separato dal primario che genera la risposta finale);
- se non sono impostate, eredita automaticamente da `LLM_PRIMARY_*` (già
  implementato da eeb8950) — zero config extra per chi non ne ha bisogno.

Il vantaggio della proposta non è "meno flessibilità sul fallback", ma "il
fallback è invocato molto più raramente": con un classificatore addestrato
(Tribuo) al posto della sola cosine similarity zero-shot, più richieste
vengono risolte al primo livello, in-process, senza round-trip LLM.

### 9.1 Misurato, non solo ipotizzato

`agent-router-trainer-maven-plugin/src/test/java/ai/gargantua/trainer/RoutingImprovementBenchmarkTest.java`
confronta i due engine sulle stesse 24 frasi *held-out* (parafrasi mai viste
in training, su 6 skill sintetiche), con la soglia di default (0.6):

| Metrica | `semantic` (zero-shot, oggi) | `tribuo` (addestrato) |
|---|---|---|
| Instradate correttamente in locale | 1/24 (4%) | 22/24 (92%) |
| Andrebbero in fallback LLM | 23/24 (96%) | 2/24 (8%) |
| Sbagliate con alta confidenza | 0/24 (0%) | 0/24 (0%) |

Con frasi parafrasate (non verbatim dagli `examples`), la cosine similarity
zero-shot contro la sola `description` produce punteggi tipicamente 0.2–0.5 —
quasi sempre sotto soglia 0.6: oggi la maggior parte delle richieste
"naturali" finisce già nel fallback LLM. Il classificatore Tribuo, addestrato
su `examples`+`routing-hints`+`description`, produce confidenze 0.7–0.99
sugli stessi casi. Nessuna delle due opzioni ha prodotto un match sbagliato
con alta confidenza: il training migliora la copertura locale, non introduce
errori nuovi.

Limiti onesti: n=24, skill sintetiche scritte a mano, non traffico di
produzione reale — indicativo dell'effetto strutturale (zero-shot vs
addestrato), non una garanzia delle percentuali esatte sul traffico di un
progetto specifico. Il test è anche un regression check permanente: fallisce
se una modifica futura rende Tribuo peggiore del baseline zero-shot.

## 10. Confronto motori ML embedded

| Criterio | ONNX Runtime | Tribuo |
|---|---|---|
| Già nel repo | **Sì** (transitivo via langchain4j) | No |
| Dipendenza nuova | Nessuna per il default; esplicitata per l'escape hatch `onnx` | 1 (`tribuo-classification-sgd`, pure Java) |
| Nativo/JNI | Sì (`.so`/`.dll` bundled) | **No — pure JVM** |
| Ecosistema server-JVM | Maturo, artifact ufficiali multipiattaforma | Maturo, nativo Oracle Labs |
| GraalVM native-image | Richiede resource-config per la lib nativa (gestibile, già implicito oggi via langchain4j) | Basso rischio se si usa la serializzazione protobuf (non Java serialization) |
| Serve training per iniziare | Solo per l'escape hatch `onnx` (serve una testa di classificazione, non solo embedding) | Sì per la testa di classificazione; **No** se si resta su `SemanticSimilarityClassifier` (zero-shot su description) |
| Caso d'uso migliore qui | Escape hatch per chi porta una testa di classificazione custom già pronta | **Motore di training di default**: pure Java, riusa l'embedding ONNX già presente come feature extractor |

> LiteRT/TFLite e DJL sono stati valutati e **scartati**: LiteRT ha un
> ecosistema Java-server debole (binding pensati per Android, poco testati su
> Linux server); DJL aggiunge un layer di astrazione multi-backend che non
> serve — un solo backend (ONNX Runtime, già in uso) copre tutti i casi
> richiesti — e il suo service-loading dinamico è tra i peggiori candidati
> per GraalVM native-image tra le opzioni valutate. Non compaiono più
> nell'architettura proposta.

## 11. Raccomandazione finale

1. **Default out-of-the-box**: `SemanticSimilarityClassifier` — refactor 1:1
   del codice già in produzione dietro l'interfaccia pluggabile, zero
   training, zero nuove dipendenze, comportamento identico a oggi.
2. **Upgrade path per accuratezza migliore**: `mvn gargantua:train-router`
   con `TribuoClassifier` — pure Java (miglior compatibilità GraalVM tra le
   opzioni valutate), riusa l'embedding ONNX già nel classpath come feature
   extractor, una sola dipendenza nuova per tutto il percorso di training.
   Misurato su un benchmark held-out (§9.1): 92% instradato in locale contro
   il 4% dello zero-shot, a parità di soglia.
3. **`OnnxClassifier`**: estensione pluggabile per team con una testa di
   classificazione già addestrata altrove, senza bisogno di un tokenizer
   (opera sullo stesso vettore di embedding di §5.1/§5.2).
4. **Fallback LLM**: resta `RoutingService` con `agent.llm.routing-model.*` /
   `LLM_ROUTING_*`, invariato — invocato solo quando il classificatore locale
   è sotto soglia, non più ad ogni richiesta ambigua nella stessa misura di
   oggi (la % di richieste che raggiungono il fallback scende con un
   classificatore addestrato).

## 12. Piano di migrazione

| Fase | Cosa cambia | Compatibilità |
|---|---|---|
| **A** | `SkillClassifier`/`ClassificationResult` in `agent-core`; `SemanticSimilarityClassifier` come refactor 1:1; `SemanticRoutingService` prende `SkillClassifier` via constructor injection. Rename `agent.routing.semantic.*` → `agent.routing.classifier.*`. `RoutingService`, `Llm.RoutingModel`, `LLM_ROUTING_*`, `agent.routing.strategy` **non cambiano**. | Solo rename di 2 property key; comportamento runtime identico a oggi con engine=semantic (default) |
| **B** (opzionale, additiva) | Nuovo modulo `agent-router-trainer-maven-plugin` + `TribuoClassifier`/`OnnxClassifier` + campo `metadata.routing-hints` in SKILL.md. | Nessuna rottura — chi non lancia `train-router` resta su semantic |

Rispetto alla bozza precedente, questa versione ha **una sola modifica
potenzialmente breaking** (rename di due property key sotto
`agent.routing.*`), non l'eliminazione di un'intera famiglia di variabili
d'ambiente già in uso.
