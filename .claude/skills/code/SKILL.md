---
name: code
description: Use when the user wants to practice coding by implementing tasks. Triggers on "/code", "kodowanie", "coding", "zadanie", "implement", "napisz kod", "coding session", "start coding". Major assigns a coding task, user implements in playground/, Major analyzes the implementation, scores, and updates mastery. Integrates with existing state system.
---

# /code — coding exercise mode

## Goal

Switch to hands-on coding mode. Major assigns implementation tasks from the coding task bank, user writes real code in the `playground/` project, Major analyzes correctness + idiomaticity + patterns, scores, and updates mastery.

## Procedure

### Step 1 — read state

Read:
- `state/current.json` — active topic, mastery level
- `state/topics.json` — mastery scores, topic status
- `state/learner_profile.json` — experience, preferences, primary_stack
- `content/coding/<active_topic>.md` — coding task bank

If `content/coding/<active_topic>.md` doesn't exist — generate it (lazy generation, CLAUDE.md sekcja 8.1). Generate 4-7 starter tasks at beginner level.

Check if `playground/` matches the user's primary stack (from `learner_profile.json`). If not, run `./scripts/scaffold-playground.sh <stack>` to set up the right project structure.

### Step 2 — select task

Pick a coding task from `content/coding/<topic>.md` based on mastery level:
- mastery < 0.3 → `recall` tasks (fill-in, complete skeleton)
- 0.3 ≤ mastery < 0.6 → `understand` tasks (implement from spec, small functions)
- 0.6 ≤ mastery < 0.8 → `apply` tasks (design + implement, multi-function, refactoring)
- mastery ≥ 0.8 → `analyze` tasks (fix anti-patterns, design decisions, code review)

Skip tasks already completed (check `state/answer_log.jsonl` for `question_id` starting with `CT-`).

### Step 3 — set up exercise

1. Update `state/current.json`:
   - `mode: "code"`
   - `active_question_id: "<task_id>"`
   - increment `questions_in_session`

2. Create exercise files in playground:
   - **Skeleton:** `playground/src/main/kotlin/com/major/playground/exercises/<TaskId>.kt`
     - Package declaration, imports, class/function signatures with `TODO()` bodies
     - Clear comments marking what to implement
   - **Test file:** `playground/src/test/kotlin/com/major/playground/exercises/<TaskId>Test.kt`
     - JUnit 5 tests that verify the requirements
     - Tests should PASS when implementation is correct
     - Use descriptive test names that hint at requirements

3. Present the task to user:
   - One Hartman phrase (max)
   - Task description (what to build)
   - Requirements (numbered list)
   - Which file to edit: exact path
   - How to verify: `cd playground && ./gradlew test --tests "*<TaskId>Test*"`
   - Do NOT show the model solution

### Step 4 — wait for user

User signals completion by:
- Saying "done", "gotowe", "sprawdź", "check", "review"
- Pasting code
- Saying "run tests"

Do NOT:
- Show the model solution before they try
- Implement it for them
- Give away the answer through "hints" that are basically the solution

If user says "nie wiem jak zacząć" / "stuck" — give ONE conceptual hint (which Kotlin feature to use), not code. On second ask, show a partial skeleton with key lines as TODO.

### Step 5 — analyze implementation

When user signals done:

1. **Read their code:** Read the exercise file from playground/
2. **Run tests:**
   ```bash
   cd playground && ./gradlew test --tests "*<TaskId>Test*" 2>&1
   ```
3. **Code review** — check against grading criteria from task bank:
   - Does it compile?
   - Do tests pass?
   - Is it idiomatic Kotlin (not "Java written in Kotlin")?
   - Does it use the right language features?
   - Are there anti-patterns?

4. **Score:**
   - `correct` (1.0): all tests pass, idiomatic code, no anti-patterns
   - `correct_with_gap` (0.7): tests pass but non-idiomatic (Java-style, verbose, missing Kotlin features)
   - `partial` (0.5): some tests pass, or concept is right but implementation has bugs
   - `incorrect` (0.0): doesn't compile, fundamental misunderstanding, or empty

5. **Feedback:**
   - What worked (brief)
   - What's wrong/missing (specific, with line references)
   - Anti-patterns spotted
   - Show model solution from task bank
   - If `correct_with_gap`: show the idiomatic version and explain why it's better
   - Interview trap if the task has one

### Step 6 — persist

1. Append to `state/answer_log.jsonl`:
   ```json
   {"ts":"ISO8601","topic":"kotlin","question_id":"CT-KT-001","bloom_level":"apply","my_answer":"<summary of what they wrote>","verdict":"partial","score":0.5,"model_answer_shown":true,"notes":"tests 3/5 passing; used Java-style for loop instead of filter/map","mode":"code"}
   ```

2. Update mastery in `state/topics.json`:
   ```
   new_mastery = 0.7 * old_mastery + 0.3 * last_score
   ```

3. Update `state/current.json` (clear active_question_id, increment counters)

### Step 7 — next task or mode switch

After feedback:
- If user wants another: pick next task (Step 2)
- If user wants drill: switch to drill mode
- If user says pause/debrief: hand off to those skills
- Interleaving: after every 3 coding tasks, offer a quick recall question from drill bank to reinforce theory

## Task bank format

Tasks live in `content/coding/<topic>.md`. Format:

```markdown
## CT-<TOPIC>-<NNN> [bloom: <level>] [difficulty: <easy|medium|hard>]
**Task:** <one-line description>
**Requirements:**
1. ...
2. ...
**Concepts tested:** <comma-separated>
**Skeleton:**
\`\`\`kotlin
// skeleton code with TODO() markers
\`\`\`
**Tests:**
\`\`\`kotlin
// JUnit 5 test code
\`\`\`
**Model solution:**
\`\`\`kotlin
// complete idiomatic solution
\`\`\`
**Grading criteria:**
- correct: ...
- correct_with_gap: ...
- partial: ...
- incorrect: ...
**Interview trap:** <optional>
```

## Important

- Coding tasks count toward mastery the SAME as drill questions (EWMA formula)
- Mode field in answer_log distinguishes `"mode":"code"` from `"mode":"drill"`
- The playground project must compile before AND after exercise files are added
- Clean up: after a task is scored, leave the exercise files in place (user reference)
- If the task bank for a topic doesn't exist yet, generate it via lazy generation (CLAUDE.md 8.1) with 4-7 starter tasks
- Playground structure adapts to user's stack via `scripts/scaffold-playground.sh` — exercise file paths adjust accordingly (e.g., Python uses `playground/src/` + `playground/tests/`, not `playground/src/main/kotlin/...`)
