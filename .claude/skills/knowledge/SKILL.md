---
name: knowledge
description: Use when the user wants to see the overall knowledge status across all topics. Triggers on "/knowledge", "what do I know", "knowledge status", "show me where I am", "report", "how ready am I". Generates tabular report with mastery percentages, question stats, and Major's commentary.
---

# /knowledge — knowledge status report

## Goal

Show the learner the global picture: where they're solid, where they're weak, how far until "interview ready".

## Procedure

### Step 1 — aggregate data

Read:
- `state/topics.json`
- `state/answer_log.jsonl` (all, but group per topic)

For each topic calculate:
- `mastery_pct` = `mastery * 100`
- `questions_asked` = count of entries in answer_log for this topic
- `correct` = entries with verdict `correct`
- `partial` = entries with verdict `partial`
- `incorrect` = entries with verdict `incorrect`
- `last_practice` = max `ts` in answer_log for this topic
- `status` from topics.json

### Step 2 — format table

Markdown table, sorted by mastery DESC (best on top):

```
| Topic               | Mastery | Questions | pass / ~ / fail | Last practice     | Status        |
|---------------------|---------|-----------|-----------------|-------------------|---------------|
| Groovy              |  84%    |   42      | 28 / 9 / 5      | 2026-05-06 18:42  | in_progress   |
| SQL                 |  61%    |   31      | 14 / 11 / 6     | 2026-05-05 19:10  | in_progress   |
...
```

### Step 3 — interview readiness score

Calculate global readiness:

```
readiness = sum(priority_weight * mastery) / sum(priority_weight)
priority_weight: critical=4, high=3, normal=2, low=1
```

Classify:
- `readiness >= 0.85` -> **READY**
- `0.65 <= readiness < 0.85` -> **CLOSE**
- `0.40 <= readiness < 0.65` -> **NEEDS WORK**
- `< 0.40` -> **NOT READY**

### Step 4 — Major's commentary

Two lines, brief, in persona, SUBSTANTIVE:

> "Overall: $READINESS_LABEL ($READINESS_PCT%). Strongest on $TOP_TOPIC ($TOP%), weakest on $WORST_TOPIC ($WORST%). Next session: drill $WORST_TOPIC."

One Hartman phrase at the end ("Pricing like a day-one recruit, but Groovy's starting to take shape.") — and only one.

### Step 5 — action suggestion

End with a concrete suggestion:
- If there's a topic with status `mastered` but `due <= now` -> "Time for `/review`."
- If `interview_date` from `learner_profile.json` is close (<= 7 days) and readiness < 0.65 -> "Mock now, regardless of mastery."
- Otherwise -> "Keep drilling on $WORST_TOPIC: `/start`."

## Important

- Do NOT start a drill in this command. This is a report, not a session.
- Do NOT scream as much here as in drill. Here you're an instructor-analyst.
- Table must be readable — align columns with spaces or use markdown table syntax.
