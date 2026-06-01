# Major Fucker — your AI drill sergeant for technical interview prep

An AI-powered study system built on Claude Code that drills you for technical interviews using active recall, mastery learning, and spaced repetition. Persona of Sergeant Hartman from *Full Metal Jacket* — but the pedagogy underneath is real.

**How it works:** paste your CV + job posting → Major analyzes the gap → generates a personalized topic plan → drills you until you're ready.

```
> /onboard
NOWY REKRUT. Zanim zaczniemy — dwa dokumenty na stół.
Wklej swoje CV (tekst lub ścieżka do pliku).

> [pastes CV]
Teraz oferta pracy.

> [pastes job posting]
ANALIZA GOTOWA.
 # | Temat              | Priorytet | Powód
---|--------------------|-----------|-----------------------------------------
 1 | Kotlin             | CRITICAL  | Wymagane w ofercie, brak na CV
 2 | Spring WebFlux     | CRITICAL  | "must have" w ofercie
 3 | SQL                | NORMAL    | Na CV, ale posting wymaga zaawansowane
Które tematy bierzemy? (numery lub "all")

> all
PLAN GOTOWY. 3 tematy na celowniku. /start i zaczynamy od Kotlin.

> /start
RUSZAMY. Kotlin, mastery 0%. Poziom: recall.
Czym różni się val od var w Kotlinie? STRZELAJ.
```

---

## Prerequisites

- [Claude Code](https://docs.anthropic.com/en/docs/claude-code) installed and authenticated
- git

## Quick Start

```bash
git clone https://github.com/YOUR_USER/major_fucker.git
cd major_fucker
claude
```

Then in Claude Code:
```
/onboard          # paste CV + job posting → personalized plan
/start            # begin drilling
```

---

## Commands

| Command | What it does |
|---------|-------------|
| `/onboard` | Personalized setup — paste CV + job posting, get a gap analysis and topic plan |
| `/start` | Begin or resume a drill session |
| `/drill` | Rapid-fire mode: 10 questions, short feedback, full debrief at end |
| `/code` | Coding exercises: implement tasks in `playground/`, get code review + score |
| `/lesson` | Mini-lecture (200-400 words) + comprehension check question |
| `/mock` | Full mock interview simulation (45-60 min, persona OFF) |
| `/review` | Spaced repetition: revisit mastered topics due for review |
| `/more` | Add a new topic manually (without CV/posting) |
| `/config` | Edit your topic selection, priorities, or preferences |
| `/knowledge` | Mastery report: table of all topics with scores |
| `/status` | Current session snapshot |
| `/next` | Skip current topic, move to next in queue |
| `/pause` | Save state, pause session (resume with `/start`) |
| `/debrief` | End-of-session reflection + plan for next time |

---

## How Personalization Works

1. **`/onboard`** parses your CV and job posting
2. Claude extracts required skills from the posting and your existing skills from the CV
3. Gap analysis determines topic priorities (critical/high/normal/low)
4. You multi-pick which topics to include
5. For each topic, Major generates a full question bank (20-32 questions across 4 Bloom levels) if one doesn't already exist
6. Study track ordered by priority and your self-assessed weakness

---

## What Major Actually Does

Every question follows this cycle:
1. Selects from the topic bank at a Bloom level matching your mastery
2. Waits for YOUR answer first (never shows the answer before you try)
3. Scores: `correct` / `partial` / `correct_with_gap` / `incorrect`
4. Shows the model answer (always, regardless of score)
5. Updates mastery via EWMA (`new = 0.7 * old + 0.3 * score`)
6. Logs to `state/answer_log.jsonl`

Mastery is structural — separate slots for theory and coding (junior/mid/senior).

---

## Customization

- **`/config`** — change topics, priorities, interview date, language after setup
- **`/more`** — add any technology topic manually
- **Playground** — coding exercises run in `playground/` (auto-scaffolded per stack: Kotlin, Python, TypeScript, Go)
- **Topic banks** — ship with pre-built banks for Kotlin, SQL, Spring, Kafka, DDD, and more. Your `/onboard` generates fresh ones for your target role.

---

## Architecture (for contributors)

```
state/                          # learner state (JSON, gitignored logs)
├── learner_profile.json        # who you are, your goal, preferences
├── topics.json                 # mastery scores, topic queue, study track
├── current.json                # active session state
├── answer_log.jsonl            # every answer (append-only)
└── session_log.jsonl           # session summaries (append-only)

content/
├── topics/<slug>.md            # question banks (theory) — 20-32 Qs per topic
├── coding/<slug>.md            # coding task banks
└── persona/                    # Hartman voice lines + feedback templates

.claude/skills/                 # Claude Code skill definitions
scripts/                        # mastery update + playground scaffolder
playground/                     # coding exercise workspace
docs/                           # pedagogy references, extending guide
```

The system is entirely file-based — no database, no web server. State persists via JSON in `state/`. Content is generated lazily: if a topic file doesn't exist when needed, Major creates it on-the-fly.

See [`docs/pedagogy.md`](./docs/pedagogy.md) for the research backing (Karpicke & Roediger 2008, Bloom 2-sigma, Cepeda 2006).

---

## FAQ

**Q: Does Major actually yell at me?**
A: Yes — Hartman persona increases emotional encoding (memory retention). But the technical feedback underneath is precise and honest.

**Q: What if Major is wrong?**
A: Correct him. He'll admit it ("Masz rację, robaku.") and update the question bank. Honesty over ego.

**Q: Can I use English?**
A: Default is Polish. Set language preference during `/onboard` or `/config`. Code and technical terms always stay in their original language.

**Q: Can I use this beyond interview prep?**
A: Yes. `/more` adds any topic. The system is curriculum-agnostic — it works for certification prep, project ramp-up, or curiosity.

**Q: What stacks are supported for coding exercises?**
A: Kotlin/Java (ships pre-built), Python, TypeScript, Go (auto-scaffolded). The playground adapts to your primary stack.

---

## License

Personal project. Persona inspired by Sergeant Hartman (R. Lee Ermey, *Full Metal Jacket*, Stanley Kubrick 1987). Technical content from author's knowledge + web research.
