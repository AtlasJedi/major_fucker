---
name: config
description: Use when the user wants to change their topic selection, priorities, or preferences after initial setup. Triggers on "/config", "zmień tematy", "dodaj moduł", "usuń temat", "change topics", "edit preferences", "konfiguracja".
---

# /config — edytuj preferencje i tematy

## Goal
Let users modify their learning plan without re-running onboarding.

## Procedure

### Step 1 — Show current config

Read `state/topics.json` and `state/learner_profile.json`. Display:

```
KONFIGURACJA

Cel: $GOAL
Firma: $COMPANY
Rozmowa: $DATE (lub "nie ustalona")

Aktywne tematy:
 # | Temat              | Priorytet | Status      | Mastery (theory)
---|--------------------|-----------|-------------|------------------
 1 | Kotlin             | CRITICAL  | in_progress | 58.9%
 2 | Coroutines         | CRITICAL  | queued      | 0.0%
 ...

Co zmieniamy?
1. Dodaj temat
2. Usuń temat
3. Zmień priorytet tematu
4. Zmień dane rozmowy (firma, data)
5. Zmień język / tempo
```

### Step 2 — Execute change

Based on user choice:

**1. Add topic:**
- Ask topic name, priority, self-assessment (like `/more` Step 1)
- Generate slug
- If `content/topics/<slug>.md` doesn't exist — generate it (lazy generation per CLAUDE.md 8.1)
- Register in `topics.json`, add to `study_track`

**2. Remove topic:**
- Show numbered list, ask which to remove
- Set status to `"removed"` in `topics.json` (don't delete progress data)
- Remove from `study_track`

**3. Change priority:**
- Show numbered list with current priorities
- Ask which topic and new priority
- Update priority, re-sort `study_track`

**4. Change interview details:**
- Ask for new company name, role, date
- Update `learner_profile.json`
- Update `job_context` in `topics.json`

**5. Change language / tempo:**
- Ask: "Język: polski / angielski? Tempo: fast / deep?"
- Update `learner_profile.json:preferences`

### Step 3 — Confirm

Show updated config table. Return to whatever mode was active before `/config`.

## Important

- Never delete mastery data when removing a topic — just set status to "removed"
- After priority change, re-sort study_track: critical first, then high, normal, low
- Keep it quick — this is a utility, not a conversation
