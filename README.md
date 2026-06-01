# major_fucker — your private drill instructor

A Claude Code agent-tutor system for learning tech under interview pressure. Persona modeled after Gunnery Sergeant Hartman from *Full Metal Jacket*, but the **pedagogy is real**: active recall, mastery learning, spaced repetition, immediate feedback. Harsh, profane, R-rated — built for adults who learn better when someone's screaming at them.

---

## Getting started

1. Open this repo in Claude Code:
   ```
   cd ~/P/major_fucker
   claude
   ```

2. Type in chat:
   ```
   /start
   ```

3. The Major runs onboarding (5 questions — 2-3 min) and fires the first question from the top-priority topic.

---

## Commands

| Command | What it does |
|---------|-------------|
| `/start` | Start or resume a session. Runs onboarding on first launch. |
| `/next` | Skip current topic, jump to the next one in queue. |
| `/knowledge` | Knowledge report: mastery table per topic + readiness score. |
| `/more` | Add a new technology / topic to the curriculum. Major builds the question bank. |
| `/drill` | Intensive mode: 10 questions back-to-back, short grades, full feedback at the end. |
| `/lesson` | Mini-lecture 200-400 words + check question. Persona reduced. |
| `/review` | Review topics due for spaced repetition (Leitner 1/3/7 sessions). |
| `/mock` | Full mock interview simulation 45-60 min. Major drops the persona entirely. |
| `/code` | Coding mode: implement a task in `playground/`, Major reviews code + scores. |
| `/status` | Snapshot of the current session (1 screen, facts only). |
| `/pause` | Save state, interrupt session. Resume with `/start`. |
| `/debrief` | Structured post-session reflection + plan for tomorrow. Closes session. |

---

## What the Major actually does

**Every question cycle:**
1. Picks a question from the topic bank at the Bloom level matching your mastery.
2. Records what was asked to `state/current.json`.
3. Waits for your answer.
4. Grades: `correct` / `partial` / `correct_with_gap` / `incorrect`.
5. Shows the model answer (always, regardless of grade).
6. Updates your mastery via EWMA (`new = 0.7 * old + 0.3 * score`).
7. Appends to `state/answer_log.jsonl` and updates `state/topics.json`.

**After session** (`/debrief`):
- Appends to `state/session_log.jsonl` with metrics.
- Updates mastery, topic statuses.
- Plan for tomorrow.

**Every few questions** the Major throws in a question from a different, already mastered topic — interleaving (retention check).

---

## Starter stack

System built for a specific **Software Engineer** interview with the stack **Groovy + SQL + REST + JSON/XML + HTML + OOP/Java** in a pricing engine platform.

| Topic | Priority | Questions in bank | Coverage |
|-------|----------|-------------------|----------|
| Groovy | critical | 32 | syntax, closures, AST, MOP, Spock, Groovy 5.0 features |
| SQL | critical | 32 | JOIN, window functions, CTE, indexes, transactions, isolation |
| OOP / Java | high | 32 | SOLID, equals/hashCode, generics, streams, concurrency, records, Java 21 |
| REST API | high | 32 | methods, idempotency, status codes, JWT, OAuth2, pagination, rate limiting |
| JSON / XML | normal | 32 | Jackson, JAXB, Slurper, parsing, validation, security (XXE, billion laughs) |
| Pricing domain | normal | 32 | waterfall, contracts, RGM, what-if, multi-currency, CPQ |
| HTML basics | low | 32 | semantic, forms, a11y, fetch, CORS, web components |

**224 questions in the starter bank**, each with a model answer + interview trap.

---

## Adding new topics

Type `/more`, the Major walks you through:
1. Technology name (e.g. "Spring Security", "Kafka", "React Hooks").
2. Goal (job interview / project / curiosity).
3. Self-assessment 1-5.
4. Priority.

The Major fills `content/topics/<slug>.md` with a question bank (min. 20 questions x 4 Bloom levels).

---

## Files it creates / updates

```
state/
├── learner_profile.json    # your profile, goal, deadline
├── topics.json             # mastery, status, due dates per topic
├── current.json            # what we're doing right now
├── answer_log.jsonl        # history of every answer (append-only)
└── session_log.jsonl       # session history (append-only)
```

---

## Philosophy

The system is built on proven pedagogical principles. See [`docs/pedagogy.md`](./docs/pedagogy.md) for references (Karpicke & Roediger 2008, Bloom 2-sigma, mastery learning, Cepeda 2006).

**The Major does NOT:**
- Give preemptive praise ("good question!", "great answer!").
- Show the answer before your attempt.
- Let you pretend you know something.
- Make up technical content — the question bank is `content/topics/*.md`.
- Bullshit. Honesty rule: "I DON'T KNOW THAT SHIT, MAGGOT, BUT I'LL VERIFY" is OK; hallucination is not.

---

## FAQ

**Q: The Major screams — is that pedagogically sound?**
A: Yes. The Hartman persona is seasoning that enhances memory encoding (emotional encoding). The substance under the screaming is precise and truthful.

**Q: What if the Major is wrong?**
A: Correct him. The Major owns it ("You're right, maggot. Fixing the bank.") and updates `content/topics/<topic>.md`. Honesty over ego.

**Q: Can I use this beyond interview prep?**
A: Yes. `/more` adds any topic. The system is curriculum-agnostic.

**Q: Where's the full structural documentation?**
A: [`CLAUDE.md`](./CLAUDE.md) — the Major's hard rulebook.

---

## License / credits

Personal project. Persona inspired by Gunnery Sergeant Hartman (R. Lee Ermey, *Full Metal Jacket*, Stanley Kubrick 1987) — not infringing on the work, just channeling the spirit of drill-sergeant pedagogy. Technical content from personal knowledge + web research.
