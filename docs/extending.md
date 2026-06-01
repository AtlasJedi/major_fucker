# Extending the Major

How to add new technologies / domains / features. Most of the time `/more` is enough — Major builds the topic himself. This document covers cases when you want to do it manually or add something more fundamental.

---

## 1. Adding a new topic (default: `/more`)

Simplest path. In a session type:

```
/more
```

Major walks you through 4 questions (name, goal, self-assessment, priority) and generates:
- File `content/topics/<slug>.md` with a question bank (min. 20 x 4 Bloom levels).
- Entry in `state/topics.json`.

After `/more` you can `/start` and Major will include the new topic in rotation by priority.

---

## 2. Adding a topic manually

When you want full control:

### Step 1 — choose a slug

Lowercase, snake_case, ASCII. E.g. "React Hooks" -> `react_hooks`.

### Step 2 — create `content/topics/<slug>.md`

Copy the structure from an existing file (e.g. `groovy.md`):

```markdown
# <Name> — question bank

> Brief context (1-2 sentences): when this topic is useful, what domain.

## Scope

- bullet list of 8-15 subtopics

## Q-<SLUG>-001 [bloom: recall]
**Question:** ...
**Model answer:** ... (5-15 sentences)
**Interview trap:** ... (2-3 sentences, classic gotchas)
**Tags:** tag1, tag2

## Q-<SLUG>-002 [bloom: recall]
...

## Q-<SLUG>-009 [bloom: understand]
...

## Q-<SLUG>-017 [bloom: apply]
...

## Q-<SLUG>-025 [bloom: analyze]
...
```

**Conventions:**
- ID format: `Q-<SLUG_UPPER>-NNN` (3-digit zero-padded).
- Bloom levels in order: recall (1-8) -> understand (9-16) -> apply (17-24) -> analyze (25-32).
- Min. 32 questions (8 per Bloom level) for critical/high priority topics.
- Min. 20 questions (5 per Bloom level) for normal/low priority.

**What makes a good question:**
- **Recall:** "what is X?", "list Y types", "what does Z function do".
- **Understand:** "explain why X", "compare A and B", "what does Y mean in the context of Z".
- **Apply:** "write code for X", "design Y for Z", "solve problem A using B".
- **Analyze:** "compare trade-offs", "show how you diagnose problem X", "defend decision Y vs Z".

**What makes a good model answer:**
- 5-15 sentences (unless the question is code-heavy — then more).
- Starts with the key point, then details.
- Concrete examples (code, formula, analogy).
- Typically contains the point the recruiter wants to hear.

**What makes a good interview trap:**
- Classic candidate mistakes (confusing A with B).
- Naive answers that sound right but are wrong.
- Follow-up questions recruiters typically ask.
- 2-3 sentences, concise.

### Step 3 — register in `state/topics.json`

```json
{
  "topics": {
    ...,
    "react_hooks": {
      "priority": "high",      // critical | high | normal | low
      "mastery": {"theory": 0.0, "coding": {"junior": 0.0, "mid": 0.0, "senior": 0.0}},
      "status": "queued",
      "due": null,
      "questions_asked": 0,
      "last_practice": null
    }
  }
}
```

### Step 4 — verify Major sees the topic

Type `/knowledge` — the topic should appear in the table at 0% mastery.

---

## 3. Adding a new command (skill)

Commands are skills in `.claude/skills/<name>/SKILL.md`. Major auto-detects them.

### Step 1 — create `.claude/skills/<command>/SKILL.md`

```markdown
---
name: <command>
description: Use when ... (specific trigger phrases). Performs ...
---

# /<command> — short title

## Goal

(1-2 sentences)

## Procedure

### Step 1 — ...
### Step 2 — ...

## Important
(notes, pitfalls, edge cases)
```

**Tips:**
- `description` must be sharp, with specific trigger phrases — Claude Code uses it for dispatching. See existing skills as reference.
- "Important" section contains anti-patterns and edge cases.
- A skill can (and should) reference `state/*` files, `content/*` files, and other skills.

### Step 2 — update the command list in README.md

Commands table in README — add a row for the new command.

### Step 3 — update `CLAUDE.md` if the command changes core behavior

If the skill modifies fundamental flow (e.g. a new mode alongside drill/lesson/mock), update section 3.4 in `CLAUDE.md`.

---

## 4. Adding Hartman phrases

`content/persona/hartman_voice.md` has categories with 30+ phrases each. To add more:

1. Open the file.
2. Find the appropriate category (greetings, praise, reprimands, transitions, pauses, special).
3. Add new phrases maintaining numbering (or renumber).
4. **Remember the hard ban:** no jokes about real disabilities. Only about intellectual laziness, ignorance, fluency illusion.

---

## 5. Modifying pedagogical rules

The Major's main rulebook is `CLAUDE.md`. Critical sections:
- **Section 2** — pedagogical rules (mastery threshold, spaced repetition intervals, interleave ratio).
- **Section 7** — mastery and topic queuing.

**Example change:** you want a less aggressive mastery threshold (from 0.85 to 0.80):

In `CLAUDE.md`:
- Section 2.5 — change the threshold.
- Section 7.3 — change the transition condition.

In skills:
- `start`, `next`, `review` — check if they reference the threshold and update.

---

## 6. Adding new Bloom levels

Default levels: `recall`, `understand`, `apply`, `analyze`. Bloom 2001 also has `evaluate` and `create`. These can be added.

**What needs updating:**
1. `CLAUDE.md` section 2.2 — level list.
2. `CLAUDE.md` section 3.2 — mastery -> level mapping.
3. Skills (`drill`, `lesson`, `start`) — where level is selected.
4. Question banks — add questions at new levels (or relabel existing ones).

**Practically:** for job interviews, 4 levels are enough. Evaluate/create are useful for creative roles, design interviews. Not necessarily Software Engineer.

---

## 7. Customizing the persona

Major is Hartman by default. Other patterns are possible:

- **Professor Snape** (cold intellectualism, sarcasm) — less screaming, more biting remarks.
- **Yoda** (stylized speech, philosophical) — not very practical for intensive drills.
- **Calm mentor** (Mr. Miyagi style) — could be like `/mock` mode permanent.

**To change the persona:**
1. Rewrite `content/persona/hartman_voice.md` -> `<persona_name>_voice.md`.
2. Update `CLAUDE.md` section 1 (Identity) — who the Major is.
3. Update skill files' references to the persona file.

**Better approach:** keep Hartman as default, add `mode: persona=<name>` in `state/learner_profile.json` and load the appropriate voice file conditionally.

---

## 8. Integration with external tools

Major operates exclusively on local files. No external API calls (apart from optional web search in `/more`). If you want to integrate:

**Anki / SuperMemo (export questions):**
- All questions in `content/topics/*.md` can be extracted by a script (Markdown-aware parser) and exported to CSV -> Anki / Mochi.
- Frontmatter `[bloom: ...]` maps to decks / tags.

**LMS (e.g. Coursera, custom platform):**
- Skills can be extended with webhooks to an external LMS.
- API call after `/debrief` to save the session in an external system.

**Speech-to-text (spoken practice):**
- Mock mode is particularly suited for voice practice.
- Whisper local + Major prompt -> authentic mock interview experience.

**These integrations require custom code outside of Major — out of scope for the basic setup.**

---

## 9. Testing new skills

After adding a skill:

1. **Smoke test:** type the triggering phrase, check that Major uses the skill (and not a different one / none).
2. **Edge cases:** missing data (empty `current.json`, no profile), invalid input, very large state files.
3. **Persistence test:** perform the action, verify state files are correctly updated (valid JSON, not corrupted).
4. **Persona test:** does Major still sound like Hartman? Did he slip into meta-mode?

---

## 10. Backup and versioning

The system saves state in `state/*` files. Recommendations:
- **Git** — `state/answer_log.jsonl` can be added to git (learning history). Or gitignored for privacy.
- **Backup** — before a major refactor (changing CLAUDE.md, mastery threshold), backup `state/*` to avoid losing progress.
- **Reset** — to start from scratch: clear `state/learner_profile.json` contents and `state/answer_log.jsonl` content. Reset `state/topics.json` to initial values.

---

## 11. Common problems

**Major doesn't pick a topic:**
- `state/topics.json` has all statuses `mastered` with `due` in the future -> nothing to do.
- Solution: `/review` or manually reset statuses.

**Major repeats the same questions:**
- Dedup problem in `current.json`.
- Solution: check that the logic for avoiding the last 30 from `answer_log.jsonl` works.

**Mastery rises/falls too fast:**
- EWMA formula `new = 0.7 * old + 0.3 * score` with alpha=0.3.
- You can use alpha=0.2 (smoother) by editing CLAUDE.md and skills.

**Persona affects substance:**
- Signal that Major is drifting. Re-check `CLAUDE.md` section 6 (Persona limits) — 1 phrase per exchange max.

---

## Bottom line

The system is complete but extensible. The most common use case (`/more`) handles 90% of needs. More advanced modifications (new commands, personas, rule adjustments) require editing `CLAUDE.md` + skills, but they're within reach — Major is ~3000 lines of markdown total, human-readable.
