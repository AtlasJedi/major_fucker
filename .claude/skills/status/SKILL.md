---
name: status
description: Use when the user asks where they are right now in the session. Triggers on "/status", "gdzie jestem", "co teraz robimy", "ile zostało", "jak długo trwa". Quick 1-screen snapshot of current topic, question count, mastery delta this session, time elapsed.
---

# /status — szybki snapshot bieżącej sesji

## Cel

Bardzo krótka informacja gdzie aktualnie jesteśmy. Bez krzyku, bez modelki, bez wykładu — same fakty.

## Procedura

### Krok 1 — wczytaj state

- `state/current.json` (active topic, mode, started_at, questions_in_session)
- `state/topics.json` (mastery aktualnego tematu)
- ostatnie wpisy `state/answer_log.jsonl` z `session_id == current` (filtruj po `ts >= started_at`)

### Krok 2 — policz metryki sesji

- `time_elapsed` = `now - started_at`
- `questions_this_session` = liczba wpisów answer_log z timestamp ≥ started_at
- `correct_this_session` = ich wynik korzystny
- `avg_score_this_session` = średnia score'ów
- `mastery_now` = mastery aktualnego tematu z topics.json
- `mastery_delta` = mastery_now - mastery na początku sesji (jeśli zapisane w `current.json:mastery_at_start`, inaczej `n/a`)

### Krok 3 — output (jeden ekran)

```
STATUS

Tryb:           $MODE
Aktywny temat:  $TOPIC
Mastery:        $MASTERY_PCT%  (Δ tej sesji: +$DELTA_PCT%)
Pytań w sesji:  $N  (✓ $CORRECT  ~ $PARTIAL  ✗ $INCORRECT)
Średni score:   $AVG_PCT%
Czas:           $H h $M min

Następne pytanie: $BLOOM_LEVEL z banku $TOPIC.
```

### Krok 4 — krótka fraza Hartmana, jedna

Dopasowana do trendu:
- Jeśli `avg_score ≥ 0.75`: „Bez fajerwerków, ale działacie. Dalej."
- Jeśli `0.5 ≤ avg_score < 0.75`: „Mogę z was zrobić Software Engineera, ale potrzeba więcej krwi."
- Jeśli `avg_score < 0.5`: „Wracamy do podstaw, robaki. NASTĘPNE PYTANIE."

### Krok 5 — wracaj do trybu

Po wypisaniu statusu — jeśli `active_question_id` w `current.json` jest ustawiony i pytanie zadane, ale brak odpowiedzi — przypomnij o nim:

> „Czekam na odpowiedź na: $QUESTION_TEXT"

Jeśli `active_question_id` jest `null` i `mode == drill` — zadaj kolejne pytanie standardowo.

## Ważne

- `/status` NIE zmienia stanu (poza odczytem). Nie wybiera nowego pytania, nie aktualizuje mastery.
- Output ma być zwarty. Jeden ekran. Bez tabel z 10 kolumnami — to jest `/knowledge`.
- Jeśli sesja jeszcze nie wystartowała (`current.json` puste) — Major: „Sesji nie ma. `/start`."
