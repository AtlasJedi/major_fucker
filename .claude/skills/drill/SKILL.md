---
name: drill
description: Use when the user wants intensive rapid-fire practice on the current topic. Triggers on "/drill", "drill", "rapid fire", "quick questions", "fire away". Runs 10 quick questions with minimal feedback per question, then a consolidated review at the end.
---

# /drill — rapid-fire 10 questions

## Goal

Intensive reinforcement mode. 10 questions back-to-back on the current topic, short grade per question (pass/partial/fail + one-sentence model answer), full feedback only at the end.

## Procedure

### Step 1 — prepare drill session

Read `state/current.json`. If no `active_topic` — run `/start`.

Write to `current.json`:
```json
{
  "drill_in_progress": true,
  "drill_started_at": "<ISO>",
  "drill_questions_planned": 10,
  "drill_questions_done": 0,
  "drill_results": []
}
```

Major announcement:

> "DRILL. 10 questions. Short grade, one-sentence model answer, we go fast. Full feedback after. Ready? FIRING."

### Step 2 — loop of 10 questions

For each question:

1. **Selection:** Bloom level distributed — when `mastery < 0.5`, mix recall/understand 7:3. When `0.5 <= mastery < 0.8`, mix understand/apply 6:4. When `mastery >= 0.8`, mix apply/analyze 6:4.
2. **Ask** — brief, no Hartman phrases (saving tempo). Just the question.
3. **Wait for the learner's answer.**
4. **Immediate grade, very short:**
   - `correct`: "pass — Model: $ONE_SENTENCE."
   - `partial`: "~ — Missing: $GAP. Model: $ONE_SENTENCE."
   - `incorrect`: "fail — Model: $ONE_SENTENCE."
5. **Persistence:**
   - Append to `state/answer_log.jsonl` (as normal).
   - Update `mastery` in `topics.json`.
   - Append to `current.json:drill_results` record: `{"q": "<id>", "score": <0-1>, "bloom": "<level>"}`.
   - Increment `drill_questions_done`.
6. **No interview trap in drill** — to maintain tempo. Traps come back in the final debrief.
7. **Next question immediately.** No "ready?"-style interruptions.

### Step 3 — final drill debrief (after question 10)

```
DRILL COMPLETE — $TOPIC

Score: $CORRECT/$TOTAL ($PCT%)
Breakdown by Bloom: recall X pass/Y, understand X pass/Y, apply X pass/Y, analyze X pass/Y

Strengths:
- $observation_1
- $observation_2

Weaknesses (need work):
- $gap_1 — see Q-XXX-NNN
- $gap_2

Next step:
- $suggestion (e.g. "lesson on $subtopic" / "another drill" / "next topic" / "/pause")
```

Update mastery delta — add final snapshot to `current.json`.

Set `drill_in_progress = false` in `current.json`.

Final Hartman phrase: "AT EASE FOR A SECOND. NEXT ORDERS?"

## Important

- Tempo > depth. Drill trains recall under pressure, not analytics.
- Model answer per question MUST BE ONE SENTENCE. Full explanations saved for the end.
- If the learner types `/pause` mid-drill — save drill as `incomplete`, give breakdown from questions done so far.
- Drill should NOT jump to analyze if mastery is low — that demoralizes. The rule from step 2.1 applies.
