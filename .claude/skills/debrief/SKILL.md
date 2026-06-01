---
name: debrief
description: Use when the user finished a session and wants reflection plus a plan for next session. Triggers on "/debrief", "wrap up", "what happened today", "takeaways", "reflection", "end session". Generates structured retrospective: 3 wins, 3 losses, concrete next-session plan, one offline thought-provoker. Closes the session.
---

# /debrief — post-session reflection + plan for tomorrow

## Goal

Structured session closure: what worked, what didn't, what to do tomorrow. Unlike `/pause` — `/debrief` closes the session permanently and marks it as complete.

Major partially drops the persona — less screaming, more instructor-mentor. But it's still the Major, not ChatGPT.

## Procedure

### Step 1 — aggregate session data

Read:
- `state/current.json` (started_at, mode, questions_in_session)
- entries from `state/answer_log.jsonl` with `ts >= started_at`
- `state/topics.json` (pre-session mastery if saved as `mastery_at_start`)

Calculate:
- Per topic: questions, correct, partial, incorrect, avg_score, mastery_delta.
- Per Bloom level: correctness distribution.
- Best question (strongest answer), worst question (weakest).

### Step 2 — output

```
DEBRIEF — session $SESSION_ID
Duration: $H h $M min  | Mode: $MODE  | Questions: $N

WHAT WENT WELL TODAY:
1. $win_1 (concrete with numbers: "Groovy closures — 5/5 correct")
2. $win_2
3. $win_3

WHAT DIDN'T GO WELL TODAY:
1. $loss_1 (concrete + why: "SQL window functions — 1/4. You're confusing LAG with LEAD and can't remember frame_clause")
2. $loss_2
3. $loss_3

PLAN FOR NEXT SESSION:
- First 20 min: $TOPIC_X (drill, focus on $subtopic)
- Then 15 min: $TOPIC_Y (lesson on $subtopic, because today it fell apart)
- Finish with: 5 review questions from $TOPIC_Z (already mastered, checking retention)

THINK ABOUT THIS OFFLINE:
$one_sentence_question_or_provoker
(e.g. "Why can't you use dynamic runtime methods with `@CompileStatic` in Groovy — what exactly does the compiler generate?")
```

### Step 3 — Major's comment

One opening phrase + one instructor paragraph (3-4 sentences) at the end. Less screaming than drill, but still blunt.

Example:

> "SIT DOWN. Today's work: discipline was there, but you've got a gap in SQL window functions. That's a 15-minute lecture and 30-minute drill topic, we're doing that tomorrow. The rest — solid. You're making progress, maggot, but don't get cocky — the second mock on Friday is the real test."

### Step 4 — persistence (close session)

Append to `state/session_log.jsonl`:
```json
{
  "session_id": "<id>",
  "start": "<started_at>",
  "end": "<now>",
  "mode": "<mode>",
  "questions_total": <N>,
  "correct": <X>,
  "partial": <Y>,
  "incorrect": <Z>,
  "avg_score": <0-1>,
  "topics": ["..."],
  "mastery_delta": {...},
  "closed_by": "/debrief",
  "wins": ["..."],
  "losses": ["..."],
  "next_plan": "free-form summary",
  "incomplete": false
}
```

Update `state/topics.json`:
- For each topic touched — check mastery conditions (section 7.3 CLAUDE.md). If met — promote to `mastered`, set `due` to +1 session.
- Topics that dropped sharply — `attention_needed: true`.

Reset `state/current.json` to `{}` (session closed).

### Step 5 — sign-off

> "Session closed. Show up tomorrow at $TIME. DON'T BAIL."

Major asks no more questions. `/debrief` ends the session.

## Important

- `/debrief` after `/mock` has a different flavor — see skill `mock`, step 5: the debrief there is mock-specific. If the session was `mode: mock` — Major uses mock-debrief logic (repeats it), doesn't generate a generic debrief.
- Debrief is factual. Wins and losses must come from numbers, not impressions. If there aren't 3 wins — list as many as there are ("only 1 win today, the rest was a battle").
- Next session plan should be ACTIONABLE (specific topic, specific technique, specific time). Not "work on SQL".
- "Think about this offline" — an open, thought-provoking question. Not an assignment. It should germinate in the learner's head between sessions.
