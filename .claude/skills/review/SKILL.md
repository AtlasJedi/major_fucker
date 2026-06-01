---
name: review
description: Use when the user wants to review previously mastered topics due for spaced repetition. Triggers on "/review", "review", "refresh", "spaced repetition", "what's slipping". Picks topics with status mastered and due <= now, runs mixed quiz across them, demotes topics that fail.
---

# /review — spaced repetition review

## Goal

Check retention of previously mastered topics. Mixed quiz from topics marked `mastered` but with an overdue `due` date. Topics that pass — advance in Leitner (box +1, longer interval). Topics that fail — drop back to `in_progress`.

## Procedure

### Step 1 — identify topics due for review

Read `state/topics.json`. Filter:
- `status == "mastered"`, AND
- `due <= now` (where `now` is the current ISO timestamp)

Sort ascending by `due` (most overdue first).

If list is empty:
> "Nothing due for review, maggot. Everything's fresh. Back to drill? (`/start`)"
End procedure.

### Step 2 — review strategy

- Question count: `min(total_review_due * 3, 15)` — 3 questions per topic, max 15 total.
- Bloom level mix: 30% understand, 40% apply, 30% analyze. Skip recall in review (too easy).
- Questions interleaved (mix topics, don't block).

Major:
> "REVIEW. $N topics in the crosshairs, $K questions. If you forgot shit — it goes back to drill."

Switch `mode` in `current.json` to `review`. Set:
```json
{
  "review_topics": ["t1", "t2", ...],
  "review_questions_planned": K,
  "review_questions_done": 0,
  "review_per_topic_results": {"t1": [], "t2": [], ...}
}
```

### Step 3 — question loop

For each question:

1. Pick topic round-robin from `review_topics`.
2. Pick question from topic bank at the level matching the mix (track counters).
3. Ask. Wait for answer.
4. Grade + model answer + interview trap (full cycle, as in drill mode per CLAUDE.md 3.2).
5. Persistence:
   - `answer_log.jsonl` as normal.
   - In `current.json:review_per_topic_results[topic]` append `{"q":"<id>","score":<0-1>}`.
   - Mastery EWMA as normal.

### Step 4 — Leitner decision per topic (after all questions)

For each topic in `review_topics` calculate `topic_review_score` = mean of scores.

- `topic_review_score >= 0.75` — **PASS**:
  - Keep `status: mastered`.
  - Advance box: if current interval is +1 session, set +3 sessions. If +3, set +7. If already +7 — stay at +7 (cap).
  - Update `due = now + new_interval`.

- `0.5 <= topic_review_score < 0.75` — **STAY**:
  - Keep `status: mastered`.
  - Box unchanged, but `due = now + 1 session` (reset to smallest box).

- `topic_review_score < 0.5` — **FAIL**:
  - `status: in_progress` (DOWNGRADE).
  - `due: null`.
  - Leave mastery as-is (EWMA already lowered it from the questions).
  - Major notes this bluntly in the final summary.

### Step 5 — final review report

```
REVIEW COMPLETE

Per topic:
- $TOPIC_1: $SCORE_PCT% — [PASS/STAY/FAIL] — next review: $DATE_OR_DROPPED_BACK
- ...

Overall: $OVERALL_SCORE_PCT%
Topics demoted to in_progress: [...] — back on the drill range.
Topics advanced (next box): [...] — returning in 3/7 days.
```

Exit `review mode` (restore `mode: drill` in current.json), select next topic per section 7.1 CLAUDE.md.

> "The ones that dropped — we attack tomorrow. The rest on cooldown. NEXT?"

## Important

- Review does NOT trigger `/start`-style onboarding. Assumes profile already exists.
- Questions in review cannot be the same as in the last drill session on that topic (dedup by `question_id` from last 30 entries in answer_log).
- If a topic has <3 questions in the bank at the designated levels — Major takes what's available plus lower levels.
