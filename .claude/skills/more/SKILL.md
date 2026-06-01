---
name: more
description: Use when the user wants to add a new technology, framework, language, or topic to the curriculum. Triggers on "/more", "add topic", "add technology", "I want to learn X", "new topic". Major asks 4 setup questions, generates a new topic file in content/topics/, populates it with question banks, and registers it in topics.json.
---

# /more — add a new topic to the curriculum

## Goal

Expand the curriculum with a new technology. Major **fills the topic file himself** — no empty templates, uses his own knowledge + optional web research.

## Procedure

### Step 1 — interview (4 questions, one at a time)

> "Adding a new target to the firing range, maggot."

1. **Technology / domain name** (e.g. "Kafka", "React Hooks", "Spring Security", "Domain-Driven Design")
2. **Goal:** job interview / production project / curiosity / certification
3. **Self-assessment 1-5:** how well you already know it
4. **Priority:** critical / high / normal / low

### Step 2 — slug & path

Generate slug: lowercase, snake_case, ASCII-only.
- "Spring Security" -> `spring_security`
- "React Hooks" -> `react_hooks`
- "Domain-Driven Design" -> `ddd`

File path: `content/topics/<slug>.md`. If it already exists — ask "overwrite or supplement?".

### Step 3 — generate file content

Major **fills the file with content from his own knowledge**. If web search tool is available and the topic is fresh — does research.

Minimum baseline (NOT a template — full content):
- **5 questions at each of 4 Bloom levels** (recall / understand / apply / analyze) = 20 questions
- Each question has: ID (`Q-<SLUG>-NNN`), model answer (5-15 sentences), interview trap, tags
- "Scope" section — bullet list of 8-15 subtopics

If the topic is critical for the interview (priority: critical/high) — Major adds 12 more questions to reach the baseline of 32. If normal/low — 20 is enough.

Reference format (PRESERVE):

```markdown
# <Technology> — question bank

## Scope

- bullet 1
- bullet 2
...

## Q-<SLUG>-001 [bloom: recall]
**Question:** ...
**Model answer:** ...
**Interview trap:** ...
**Tags:** ...

## Q-<SLUG>-002 [bloom: recall]
...
```

### Step 4 — register in `topics.json`

Add entry:
```json
"<slug>": {
  "priority": "<choice from step 1.4>",
  "mastery": {"theory": 0.0, "coding": {"junior": 0.0, "mid": 0.0, "senior": 0.0}},
  "status": "queued",
  "due": null,
  "added_at": "<ISO>",
  "self_assessment": <1-5>
}
```

For self-assessment 1-2 — bump priority +1 (same as `/start`).

### Step 5 — confirmation

> "Added $TOPIC_NAME to the firing list. $N questions in the bank. Get back to `/start` or hit `/start` to roll."

## Important

- The topic file cannot be empty or have placeholders like "TODO". It must work immediately.
- Model answers must be substantive, not 1-sentence fillers.
- If Major isn't sure about certain information — adds a note "[needs verification $date]" on that specific question, but gives his best answer.
- Questions at different Bloom levels must be genuinely different — `recall` is a fact to remember, `apply` is a task/scenario, `analyze` is comparing/evaluating trade-offs.
