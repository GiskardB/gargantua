# Handoff — riconciliazione su `develop`

**Data:** 2026-08-05 · **Sessione:** `session_012kEHWo1XE4pHGYbSMT6tLZ` · **Versione:** 1.2.20 (9 moduli)

Documento per riprendere il lavoro da una sessione locale con credenziali git.
La sessione che lo ha prodotto ha perso l'accesso in scrittura al remoto a metà
lavoro: il proxy git è sparito e non sono rimaste credenziali. Tutto il codice è
committato e verificato, ma **due commit non sono mai arrivati su GitHub**.

---

## 1. TL;DR — cosa manca davvero

| | |
|---|---|
| Lavoro framework su GitHub | **11 commit su 13**, su `claude/ai-operating-system-runtime` |
| Non su GitHub | **2 commit**: il merge su `develop` + un fix da 20 righe |
| Lavoro esempi su GitHub | **0 commit su 2** — push verso `gargantua-examples` risponde 403 |
| Stato verifica | 823 test framework verdi, 22/22 esempi verdi sull'albero mergiato |
| Azione richiesta | un `git push` fast-forward, più il push degli esempi |

Il push mancante su `develop` è un **fast-forward puro**. Verificato:

```
origin/develop  ⊂  origin/main  ⊂  develop (locale)
```

`origin/develop` è antenato diretto di `develop` locale: 0 commit divergenti. Nessun merge, nessun conflitto, nessun `--force`.

---

## 2. Cosa c'è già su GitHub

Undici dei tredici commit di questo lavoro sono sul remoto, su
`claude/ai-operating-system-runtime`. In ordine cronologico:

```
1e3381d  docs: add Runtime Observability requirements
37e3bd9  refactor: cleanup codebase — remove dead configs, fix imports
c51a33c  fix: align documentation with implementation, fix guardrail toggle bug
d280ac3  docs: add AI Operating System architecture vision
51a9936  feat(core): workload, capability and bundle domain model
9f287d8  fix(engine): repair broken build in MongoCostTrackingRepository
c3d39c0  feat: MCP client and composable tool providers
5d83514  feat: bundle format, loader and standalone runtime
6dabaac  docs: reconcile documentation with the runtime work
6a49af1  fix(engine): restore error payload for tool exceptions without @ToolRetry
51ebed0  feat(runtime): embedded profile so a bundle can run without infrastructure
```

I primi tre sono anche su `claude/ai-agent-boilerplate-7mTGQ`.

**Nulla di questo lavoro è a rischio.** È tutta roba recuperabile dal remoto
anche se questa macchina sparisse.

## 3. Cosa NON c'è su GitHub

Solo due commit, entrambi in cima a `develop` locale. Le SHA qui sotto sono
quelle dell'albero locale al momento della scrittura: se i commit vengono
riscritti (per esempio con `--amend --reset-author`) cambiano, ma i messaggi
restano quelli.

### `4e4ae42` — Merge `claude/ai-operating-system-runtime` in `develop`

Il commit di merge che porta insieme i 39 commit di `main` e gli 11 sopra.
Non è un merge automatico: **24 file sono stati toccati da entrambi i lati** e
le risoluzioni sono state fatte a mano. I file in conflitto:

```
README.md
pom.xml
agent-archetype/.../application.yml
agent-core/.../core/tool/ToolDefinition.java
agent-engine/pom.xml
agent-engine/.../adapters/web/AuditAdminController.java
agent-engine/.../adapters/web/CostAdminController.java
agent-engine/.../adapters/web/FlowController.java
agent-engine/.../adapters/web/GuardrailAdminController.java
agent-engine/.../adapters/web/ToolCacheAdminController.java
agent-engine/.../autoconfigure/AgentAutoConfiguration.java
agent-engine/.../autoconfigure/AgentCardService.java
agent-engine/.../autoconfigure/AgentProperties.java
agent-engine/.../autoconfigure/AgentSkillProcessor.java
agent-engine/.../autoconfigure/DefaultOrchestratorEngine.java
agent-engine/.../autoconfigure/ToolRegistry.java
agent-engine/.../autoconfigure/WebAutoConfiguration.java
agent-memory-sdk/.../adapters/inmemory/InMemoryVectorStore.java
docs/deployment.md   docs/extending.md   docs/guardrails.md
docs/memory-system.md   docs/skills-and-routing.md   docs/tools-and-annotations.md
```

Decisioni di risoluzione da conoscere, se dovessi rifare il merge da zero:

- **`ToolRegistry`** — presa la versione di `main` per intero (gate RBAC, gate
  di approvazione HITL con semantica di consumo, cache, retry) e innestate sopra
  le aggiunte del runtime: `additionalProviders`, la mappa `providerRouting`,
  `registerProviderTools()`, `@PreDestroy shutdown()`, il routing verso i
  provider dentro `executeTool`, il mapper di tipi `addProperty(...)`.
- **`ToolDefinition`** — unite entrambe le versioni. L'array
  `approvalShowParameters` **non va clonato** nel constructor compatto: un record
  confronta le componenti array per identità, e un test di `main` dipende dal
  fatto che due descrittori equivalenti condividano lo stesso array vuoto.
  Clonarlo rompe silenziosamente quel test.
- **`AgentProperties`** — versione di `main` più la classe annidata `McpClient`
  reintrodotta (`enabled`, `requestTimeoutSeconds`, `failFast`, `servers[]`).
- **`AnnotationToolProvider`** — eliminato, era ridondante dopo il merge. La
  scansione delle annotazioni resta dentro `ToolRegistry`, accanto ai gate che
  leggono quelle stesse annotazioni; `ToolProvider` copre solo le sorgenti
  esterne. Sei punti della documentazione che descrivevano ancora la classe
  eliminata sono stati corretti.

### `5fa1de9` — `fix(engine): populate ToolDefinition.parameters`

Venti righe in `ToolRegistry.java`. I tool scansionati da `@AgentTool` non
popolavano `ToolDefinition.parameters`, che restava vuota: il metodo
`parametersOf(method)` legge i parametri del metodo e li riporta nel
descrittore. I tipi restano annunciati come stringhe per i tool Java, coerente
con come gli argomenti vengono convertiti all'invocazione.

Bug intercettato da `agent-example-mcp-client`, che verifica che il descrittore
di un metodo `@AgentTool` riporti i nomi dei suoi parametri.

## 4. Come recuperare i due commit

Sono stati salvati come git bundle e come patch in **`/home/user/gargantua-handoff/`**
(fuori dal repo, per non sporcarlo). I bundle sono verificati validi.

```
/home/user/gargantua-handoff/
├── gargantua-develop.bundle     # ref develop, tutti i commit mancanti al remoto
├── gargantua-patches/           # gli stessi commit come patch applicabili
├── examples-pending.bundle      # ref main, i 2 commit degli esempi
└── examples-patches/
```

**Se hai ancora questa macchina** — il modo più semplice è pushare direttamente
da `/home/user/gargantua`, i commit sono già lì:

```bash
cd /home/user/gargantua
git push origin develop        # fast-forward, nessun conflitto possibile
```

**Da un'altra macchina** — recupera dal bundle:

```bash
git clone https://github.com/GiskardB/gargantua && cd gargantua
git fetch /percorso/gargantua-develop.bundle develop:develop-recovered
git push origin develop-recovered:develop
```

**Se i bundle sono andati persi** — rifai il merge a mano, seguendo le decisioni
di risoluzione della sezione 3:

```bash
git checkout develop && git merge origin/main
git merge origin/claude/ai-operating-system-runtime
```

## 5. Repository esempi — nessun push riuscito

`GiskardB/gargantua-examples` è **fuori dallo scope** dei tool GitHub concessi a
questa sessione, e il push git risponde 403. Lettura sì, scrittura no. Due
commit sono pronti ma mai partiti:

| Commit | Contenuto |
|---|---|
| `fffe88d` | `fix: guardrail configuration keys that bound to nothing` — 17 file, le chiavi `application.yml` dei guardrail in 16 esempi si legavano a proprietà inesistenti (no-op silenzioso) |
| `c0f7c7f` | `feat: MCP client and runtime bundle examples, fix RAG example` |

Il secondo aggiunge due esempi nuovi e ne ripara uno rotto:

- **`agent-example-mcp-client`** — modalità libreria con un server MCP montato
  come `ToolProvider` aggiuntivo, affiancato ai metodi `@AgentTool`.
- **`agent-example-runtime-bundle`** — modalità runtime: un `.gbundle`
  dichiarativo eseguito dall'immagine generica `gargantua-runtime`, **senza una
  riga di codice applicativo**. Include `verify.sh`, che lo avvia e verifica la
  agent card.
- **`agent-example-rag`** — rotto dalla 1.2.18 in poi. Il seeder testava
  `instanceof InMemoryVectorStore`, ma il profilo embedded registra
  `EmbeddingInMemoryVectorStore`: il seeding non faceva nulla, in silenzio. Reso
  agnostico rispetto allo store via reflection, e `rag-min-score` alzato da 0.05
  a 0.30, valore da cui dipendono le assert sul contesto recuperato.

Il working copy sta in
`/tmp/claude-0/-home-user-gargantua/9a7fe138-b03c-5c91-9eab-f1b7e8da2743/scratchpad/examples`,
che è temporaneo — usa il bundle in `/home/user/gargantua-handoff/`. Per pushare:

```bash
git clone https://github.com/GiskardB/gargantua-examples && cd gargantua-examples
git fetch /percorso/examples-pending.bundle main:examples-recovered
git merge examples-recovered && git push origin main
```

Il branch `claude/example-mcp-devops-agent` su `gargantua` va **tenuto** finché
questo trasferimento non è fatto: contiene la patch dei guardrail e i due esempi
nuovi.

## 6. Stato della verifica

Tutto verificato sull'albero mergiato, non su previsione:

- **823 test del framework** verdi (`mvn test`, 9 moduli)
- **22 esempi su 22** verdi (~220 test), buildati contro il framework mergiato
  pubblicato in locale sotto le coordinate JitPack
- **Modalità runtime provata end-to-end**: la agent card risponde
  `{"name":"support-agent","skills":[{"id":"answer-faq",...}]}`, health UP
- **Compatibilità all'indietro provata** costruendo un progetto consumer reale e
  avviando il context Spring completo
- I nomi delle proprietà nella documentazione verificati riflettendo su
  `AgentProperties` (149 path bindabili) e confrontandoli con ogni riferimento
  `agent.*` nei documenti

Il profilo `application-embedded.yml` del runtime esclude le autoconfig di Mongo
e Redis: il boot passa da **41s a 6.5s**.

## 7. Un cambio di comportamento deliberato

Nel merge è entrato un cambiamento di default che vale la pena conoscere prima
di rilasciare:

- `Fallback` → default `anthropic` (prima ereditava il provider primario)
- `RoutingModel` → default `ollama`

Motivo: un fallback che eredita il provider del primario non è un fallback — se
il primario è giù, lo è anche il fallback. `maxTokens` è stato invece lasciato al
valore di `main` (4096) e la documentazione corretta di conseguenza.

## 8. Cosa resta da fare

Bloccato solo dall'accesso al remoto:

```bash
cd /home/user/gargantua
git push origin develop
```

Poi, se vuoi (era stato chiesto e poi messo da parte — i branch per ora restano):

```bash
git push origin --delete claude/amazing-thompson-U0hI3        # identico a main
git push origin --delete claude/ai-agent-boilerplate-7mTGQ    # contenuto in develop
git push origin --delete claude/ai-operating-system-runtime   # contenuto in develop
```

Lavoro tecnico non ancora affrontato, tutto tracciato ma non iniziato:

- verifica della firma dei bundle (`BundleLoader` calcola già il checksum SHA-256,
  ma non verifica firme)
- enforcement di RBAC e memory layer a livello di workload — oggi
  `ManifestProperties.unappliedFields()` li **riporta esplicitamente come non
  applicati** invece di fingere che lo siano
- `CatalogRegistrar`, quando la Catalog API esisterà
- **Fase 2 (Control Plane)** — repository separato, non iniziata

---

## Nota su questo file

L'ho scritto per rendere ripartibile un lavoro che non sono riuscito a
consegnare fino in fondo. La riconciliazione e la verifica sono complete; quello
che manca è esclusivamente il trasporto verso il remoto. Se qualcosa qui non
torna con quello che trovi nel repo, fidati del repo: i commit sono la fonte di
verità, questo è solo il loro indice.
