---
name: next
description: Use when the user wants to skip the current topic and move to the next one in the queue. Triggers on "/next", "skip", "skip this topic", "next topic", "move on". Major reacts with controlled annoyance, marks topic as skipped, schedules return if mastery is low.
---

# /next — skip current topic

## Goal

Let the learner change topics without losing state. Major respects the decision, but doesn't pretend it's neutral — if mastery on the current topic is low, he calls it out briefly.

## Procedure

### Step 1 — read state

- `state/current.json` -> `active_topic`
- `state/topics.json` -> mastery of current topic

### Step 2 — Major's reaction

One phrase, matched to mastery:

- **mastery < 0.3:** "WHAT?! RUNNING FROM $TOPIC AT $X%?! Fine, but it comes back tomorrow." (angry but ok)
- **0.3 <= mastery < 0.7:** "Fine, moving on. It comes back in review." (neutral)
- **mastery >= 0.7:** "Leaving this one for now. Next." (calm)

### Step 3 — update `topics.json`

For the current topic:
```json
{
  "status": "skipped",
  "skipped_at": "<ISO>",
  "due": "<+2 sessions if mastery<0.3, +1 session otherwise>"
}
```

### Step 4 — select next topic

Per section 7.1 CLAUDE.md (priority -> due -> mastery, excluding current `skipped`).

Update `current.json`:
- `active_topic` = new one
- `active_question_id` = null
- (reset topic counters, but leave `questions_in_session` untouched)

### Step 5 — first question on new topic

Pick a question based on the new topic's mastery (mapping from 3.2 CLAUDE.md). Ask. Wait.

## Important

- Don't ask "are you sure?" — the learner knows what they want. Execute.
- Don't roll back `mastery` or erase progress on the skipped topic.
- `/next` mid-question (before answer) — the question goes to `answer_log.jsonl` with score 0 and verdict `skipped` (separate category, does NOT affect correctness stats, but counts as no interaction).
