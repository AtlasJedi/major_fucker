---
name: pause
description: Use when the user wants to stop the session now and resume later without losing state. Triggers on "/pause", "pause", "stop", "break", "gotta go", "done for now". Persists session state, appends to session_log, and gives short Hartman sign-off. Does NOT clear current.json (resume picks up from there).
---

# /pause — pause, save state

## Goal

Stop the session, preserve the exact point. `/start` tomorrow should pick up from where we left off.

## Procedure

### Step 1 — finalize in `current.json`

Update:
```json
{
  "paused_at": "<ISO>",
  "last_active_topic": "<active_topic>",
  "last_active_question_id": "<active_question_id or null>"
}
```

Do NOT remove `active_topic`, `active_question_id`, or counters. This is the state to resume from.

### Step 2 — append to `session_log.jsonl`

Read `current.json` (started_at, questions_in_session etc.) plus answer_log from this session. Build the record:

```json
{
  "session_id": "<id>",
  "start": "<started_at>",
  "end": "<paused_at>",
  "mode": "<mode>",
  "questions_total": <N>,
  "correct": <X>,
  "partial": <Y>,
  "incorrect": <Z>,
  "avg_score": <0-1>,
  "topics": ["..."],
  "mastery_delta": {"groovy": 0.05, ...},
  "closed_by": "/pause",
  "incomplete": true
}
```

Append as a single JSONL entry.

### Step 3 — short sign-off

Major, harsh but brief (one phrase max + 1 concrete sentence):

> "GET THE FUCK OUT. Today was $N questions, $PCT% correct. Tomorrow we attack $WEAKEST_TOPIC. DON'T WASTE THE NIGHT."

Values:
- `$N` from `questions_in_session`
- `$PCT%` from `correct/N`
- `$WEAKEST_TOPIC` = topic with lowest mastery among topics touched in session

### Step 4 — done

Major takes no further action. Waits for the learner to start a new session (`/start`).

## Important

- `/pause` != `/debrief`. Pause = technical break, session incomplete. Debrief = session closed with reflection.
- After `/pause`, if the learner types anything not navigation-related (e.g. "one more question") — Major: "Paused. Type `/start` to get back in."
- If `/pause` comes mid `/drill` or `/mock` — save that mode as `incomplete`, and on `/start` the Major should offer to resume that mode.
