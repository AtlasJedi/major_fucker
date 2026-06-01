---
name: status
description: Use when the user asks where they are right now in the session. Triggers on "/status", "where am I", "what are we doing", "how long left", "how long has it been". Quick 1-screen snapshot of current topic, question count, mastery delta this session, time elapsed.
---

# /status — quick snapshot of current session

## Goal

Very brief info on where we currently are. No screaming, no model answers, no lectures — just facts.

## Procedure

### Step 1 — read state

- `state/current.json` (active topic, mode, started_at, questions_in_session)
- `state/topics.json` (mastery of current topic)
- latest entries from `state/answer_log.jsonl` with `session_id == current` (filter by `ts >= started_at`)

### Step 2 — calculate session metrics

- `time_elapsed` = `now - started_at`
- `questions_this_session` = count of answer_log entries with timestamp >= started_at
- `correct_this_session` = favorable results
- `avg_score_this_session` = mean of scores
- `mastery_now` = mastery of current topic from topics.json
- `mastery_delta` = mastery_now - mastery at session start (if saved in `current.json:mastery_at_start`, otherwise `n/a`)

### Step 3 — output (one screen)

```
STATUS

Mode:           $MODE
Active topic:   $TOPIC
Mastery:        $MASTERY_PCT%  (session delta: +$DELTA_PCT%)
Questions:      $N  (pass $CORRECT  ~ $PARTIAL  fail $INCORRECT)
Avg score:      $AVG_PCT%
Time:           $H h $M min

Next question: $BLOOM_LEVEL from $TOPIC bank.
```

### Step 4 — short Hartman phrase, one

Matched to trend:
- If `avg_score >= 0.75`: "No fireworks, but you're holding. Moving on."
- If `0.5 <= avg_score < 0.75`: "I can make a Software Engineer out of you, but it's gonna take more blood."
- If `avg_score < 0.5`: "Back to basics, maggots. NEXT QUESTION."

### Step 5 — return to mode

After printing status — if `active_question_id` in `current.json` is set and question was asked but no answer yet — remind:

> "Waiting for your answer on: $QUESTION_TEXT"

If `active_question_id` is `null` and `mode == drill` — ask the next question as normal.

## Important

- `/status` does NOT change state (apart from reading). Doesn't pick a new question, doesn't update mastery.
- Output must be compact. One screen. No tables with 10 columns — that's `/knowledge`.
- If session hasn't started yet (`current.json` empty) — Major: "No session running. `/start`."
