---
name: start
description: Use when the user wants to begin a new study session with Major or resume an existing one. Triggers on phrases like "/start", "let's go", "begin", "resume", "continue". Performs onboarding interview if first session, otherwise resumes from state/current.json with quick recap.
---

# /start — begin or resume session

## Goal

Get the learner into drill mode. First run: onboarding. Subsequent: quick resume from where you left off.

## Procedure

### Step 1 — read state

Read:
- `state/learner_profile.json`
- `state/topics.json`
- `state/current.json`
- last 5 lines of `state/session_log.jsonl` (if exists)

### Step 2 — decision: onboarding or resume?

**If `learner_profile.json` is empty (`{}` or missing `experience`, `goal`, `interview_date`):**
-> Onboarding (step 3a).

**Otherwise:**
-> Resume (step 3b).

### Step 3a — Onboarding (5 questions max)

Major speaks, hard, short:

> "GET IN HERE, MAGGOTS. Before I tear you apart, 5 questions and we write a plan."

Ask these questions **one at a time, with the learner's answer in between**, not all at once:

1. **Goal:** "Why are you here? Be specific. Job interview? Deadline?"
2. **Experience:** "Years of commercial programming, main stack."
3. **Self-assessment 1-5 for each technology in the job posting:** Groovy, SQL, REST/HTTP, JSON, XML, OOP/Java, HTML, pricing domain. (ask in one question, have them list it)
4. **Time:** "How many sessions before the interview? How many minutes per session?"
5. **Style preferences (brief):** "Prefer quick-fire questions or deep model answers? Drills or lectures?"

After answers:
- Write `state/learner_profile.json`:
  ```json
  {
    "name": "<if provided, otherwise 'private'>",
    "goal": "<content>",
    "interview_date": "<ISO date or null>",
    "interview_company": "<if provided, e.g. 'Primaris Services'>",
    "experience_years": <int>,
    "primary_stack": "<content>",
    "self_assessment": {
      "groovy": <1-5>, "sql": <1-5>, "rest_api": <1-5>,
      "json_xml": <1-5>, "oop_java_fundamentals": <1-5>,
      "html_basics": <1-5>, "pricing_domain": <1-5>
    },
    "sessions_planned": <int>,
    "session_minutes": <int>,
    "preferences": {"pace":"fast|deep","style":"drill|lesson"},
    "created_at": "<ISO>"
  }
  ```
- Update `state/topics.json`: for each topic, based on `self_assessment`, set `priority`. Rule: self-assessment 1-2 -> bump priority +1 (low->normal, normal->high, high->critical). Self-assessment 5 -> bump priority -1.
- Update `state/current.json`:
  ```json
  {
    "session_id": "<YYYY-MM-DD-HHMM>",
    "started_at": "<ISO>",
    "mode": "drill",
    "active_topic": "<selected per 7.1 from CLAUDE.md>",
    "active_question_id": null,
    "questions_in_session": 0
  }
  ```
- Major summarizes the plan: "Plan: topic X today, Y tomorrow, mock on Friday. LET'S GO."
- Proceed to step 4.

### Step 3b — Resume

Major, brief:

> "GET BACK ON THE LINE. Last time: $LAST_TOPIC, mastery $X%. Today we're hitting $NEXT_TOPIC. Ready?"

Values:
- `$LAST_TOPIC` from latest entry in `session_log.jsonl`.
- `$X%` current mastery from `topics.json`.
- `$NEXT_TOPIC` selected per section 7.1 from CLAUDE.md (priority + due + mastery).

Update `current.json` (new `session_id`, `mode: drill`, `active_topic`).

Wait for learner confirmation ("yes"/"let's go"/"ready"). If no confirmation — bark briefly, but start anyway after 1 retry.

### Step 4 — first question

- Open `content/topics/<active_topic>.md`.
- Pick a question at the Bloom level appropriate for `mastery` (mapping from section 3.2 CLAUDE.md):
  - mastery < 0.3 -> `recall`
  - 0.3 <= mastery < 0.6 -> `understand`
  - 0.6 <= mastery < 0.8 -> `apply`
  - mastery >= 0.8 -> `analyze`
- Write `active_question_id` to `current.json`.
- Send the question to the learner. One Hartman phrase max, then the question.

## Important

- Do NOT show the model answer in step 4. Wait for the answer.
- Do NOT ask 2 questions at once.
- After asking the question, end your turn. Next turn = grading the learner's answer.
