# Handoff — riconciliazione su `develop`

**Aggiornato:** 2026-08-05 · **Sessione:** `session_012kEHWo1XE4pHGYbSMT6tLZ` · **Versione:** 1.2.20 (9 moduli)

> **Stato: framework fatto, esempi in sospeso.**
> `develop` è stato pushato ed è allineato con il remoto. Resta da trasferire il
> lavoro sul repository degli esempi, per cui manca il permesso di scrittura.

---

## 1. Cosa è stato fatto

`develop` è ora il branch più aggiornato: contiene i 39 commit di `main` più i 13
del lavoro di Phase 1 (AI Operating System runtime). Il merge non era automatico —
24 file toccati da entrambi i lati, risolti a mano — ed è verificato:

- **823 test del framework** verdi (`mvn test`, 9 moduli)
- **22 esempi su 22** verdi (~220 test), buildati contro il framework mergiato
- **Modalità runtime provata end-to-end**: la agent card risponde
  `{"name":"support-agent","skills":[{"id":"answer-faq",...}]}`, health UP
- **Compatibilità all'indietro provata** costruendo un progetto consumer reale e
  avviando il context Spring completo
- Nomi delle proprietà nella documentazione verificati riflettendo su
  `AgentProperties` (149 path bindabili) e confrontandoli con ogni riferimento
  `agent.*` nei documenti

Il profilo `application-embedded.yml` del runtime esclude le autoconfig di Mongo
e Redis: il boot passa da **41s a 6.5s**.

## 2. Cosa resta: il repository degli esempi

`GiskardB/gargantua-examples` non è stato aggiornato. Il PAT fornito è
fine-grained e il suo scope **non include quel repository**: il push risponde
`403 — Permission to GiskardB/gargantua-examples.git denied`.

Due commit sono pronti e verificati ma mai partiti:

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

### Come completarlo

I due commit sono salvati come bundle e come patch, verificati validi:

```
/home/user/gargantua-handoff/
├── examples-pending.bundle      # ref main, i 2 commit — SERVE ANCORA
├── examples-patches/            # gli stessi commit come patch
├── gargantua-develop.bundle     # ormai ridondante, develop è pushato
└── gargantua-patches/           # idem
```

Il working copy sta in
`/tmp/claude-0/-home-user-gargantua/9a7fe138-b03c-5c91-9eab-f1b7e8da2743/scratchpad/examples`,
che è temporaneo: usa il bundle.

```bash
git clone https://github.com/GiskardB/gargantua-examples && cd gargantua-examples
git fetch /percorso/examples-pending.bundle main:examples-recovered
git merge examples-recovered && git push origin main
```

In alternativa, estendere lo scope del PAT a `gargantua-examples` e rifare il
push da qui.

Finché il trasferimento non è fatto, il branch `claude/example-mcp-devops-agent`
su `gargantua` va **tenuto**: contiene la patch dei guardrail e i due esempi.

## 3. Un cambio di comportamento deliberato

Nel merge è entrato un cambiamento di default che vale la pena conoscere prima
di rilasciare:

- `Fallback` → default `anthropic` (prima ereditava il provider primario)
- `RoutingModel` → default `ollama`

Motivo: un fallback che eredita il provider del primario non è un fallback — se
il primario è giù, lo è anche il fallback. `maxTokens` è stato invece lasciato al
valore di `main` (4096) e la documentazione corretta di conseguenza.

## 4. Note sulla risoluzione del merge

Utili se in futuro serve capire perché il codice è come è:

- **`ToolRegistry`** — presa la versione di `main` per intero (gate RBAC, gate di
  approvazione HITL con semantica di consumo, cache, retry) e innestate sopra le
  aggiunte del runtime: `additionalProviders`, la mappa `providerRouting`,
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

## 5. Branch

Non sono stati cancellati, per scelta. Quando vorrai:

```bash
git push origin --delete claude/amazing-thompson-U0hI3        # identico a main
git push origin --delete claude/ai-agent-boilerplate-7mTGQ    # contenuto in develop
git push origin --delete claude/ai-operating-system-runtime   # contenuto in develop
```

`claude/example-mcp-devops-agent` va tenuto, vedi §2.

## 6. Lavoro tecnico non ancora affrontato

Tracciato ma non iniziato:

- verifica della **firma** dei bundle (`BundleLoader` calcola già il checksum
  SHA-256, ma non verifica firme)
- enforcement di RBAC e memory layer a livello di workload — oggi
  `ManifestProperties.unappliedFields()` li **riporta esplicitamente come non
  applicati** invece di fingere che lo siano
- `CatalogRegistrar`, quando la Catalog API esisterà
- **Fase 2 (Control Plane)** — repository separato, non iniziata

La visione architetturale complessiva sta in
[`docs/architecture/ai-operating-system.md`](docs/architecture/ai-operating-system.md),
le decisioni vincolanti in
[`docs/architecture/runtime-decisions.md`](docs/architecture/runtime-decisions.md).
