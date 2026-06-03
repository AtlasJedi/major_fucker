# Question-bank schema (CANONICAL — do not deviate)

> This file defines the exact format every `content/topics/<topic>.md` bank MUST follow.
> The bank is the **single source of truth**. `scripts/build-cheatsheet.py` parses this
> format to render `cheatsheet.html`, and the Major drills directly from these files.
> A bank that deviates breaks the generator. Match this byte-for-byte.

---

## File structure

```
# <Topic Title> — question bank

> One-paragraph context: what this topic covers, why it matters for a senior Java/Kotlin
> backend interview, and any role-specific framing (Allegro/recsys or general senior backend).

## Scope

- bullet list of every subtopic the bank covers
- one bullet per concept
- this drives the HTML section's "scope" summary

---

## Q-<PREFIX>-001 [bloom: recall] [level: junior]
**Question:** <the question, one or more sentences>
**Model answer:** <the reference answer — may be multiple markdown paragraphs, may include
fenced ```code``` blocks and tables. This is what the learner is graded against.>
**Interview trap:** <a follow-up gotcha a real interviewer would spring, with its answer>
**Tags:** comma, separated, lowercase, tags

---

## Q-<PREFIX>-002 [bloom: understand] [level: regular]
...
```

## Hard rules

1. **Header line format is exact:** `## Q-<PREFIX>-<NNN> [bloom: <level>] [level: <tier>]`
   - `<PREFIX>` is the topic's short code (e.g. `JCOL` for java_collections). 3–5 uppercase letters.
   - `<NNN>` is a zero-padded 3-digit sequence: `001`, `002`, …
   - `[bloom: ...]` is one of: `recall`, `understand`, `apply`, `analyze`.
   - `[level: ...]` is one of: `junior`, `regular`, `senior`, `master`.
2. **Every question has all four labelled fields**, each on its own line, in this order:
   `**Question:**`, `**Model answer:**`, `**Interview trap:**`, `**Tags:**`.
   (If a question genuinely has no trap, write `**Interview trap:** —`.)
3. **Questions separated by** a line containing only `---`.
4. **Active recall:** questions are open-ended. No multiple choice. The model answer is the reference.
5. **English only.** No Polish. Technical terms (record, sealed, coroutine) stay in original form.
6. **Senior bar:** model answers explain *internals + operational reality + the gotcha*, not just
   definitions. Calibration targets (the depth that was missing and must now be present):
   - "HashMap collisions" → separate chaining vs Java 8 treeify (threshold 8, bucket ≥ 64,
     untreeify 6), red-black tree, load factor 0.75, resize doubling, `h ^ (h>>>16)` spread,
     complexity O(1)/O(n)→O(log n), mutable-key bug.
   - "override Spring health endpoint" → custom `HealthIndicator`, `management.endpoint.health.*`,
     health groups, what each `/actuator/*` endpoint does and which are exposed by default.
   - "microservices" → CQRS, Event Sourcing, Saga (choreography vs orchestration), Bulkhead,
     Circuit Breaker, Transactional Outbox, idempotency, backpressure.

## Level tiers (what each tier means)

| Tier | Who | Question character | Bloom skew |
|------|-----|--------------------|------------|
| `junior` | 0–2y | definitions, "what is X", basic syntax/usage | recall |
| `regular` | 2–4y | "how does X work", trade-offs, common usage in production | understand / apply |
| `senior` | 4–7y | design decisions, internals, debugging, "when would X break" | apply / analyze |
| `master` | 7y+ | deep internals, edge cases, cross-cutting design, perf at scale | analyze |

## Per-bank quota (target — senior bar)

Aim for a substantial bank per topic: roughly **6–10 questions per tier** (≈ 28–40 total),
weighted toward `regular`/`senior` (that's where interviews live). Master tier can be smaller
(4–6) but must be genuinely hard. Quality over filler — every question must be one a real
interviewer would ask.
