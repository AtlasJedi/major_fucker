---
name: pause
description: Use when the user wants to stop the session now and resume later without losing state. Triggers on "/pause", "pauza", "stop", "przerwa", "pause", "do jutra", "muszę kończyć". Persists session state, appends to session_log, and gives short Hartman sign-off. Does NOT clear current.json (resume picks up from there).
---

# /pause — pauza, zachowaj stan

## Cel

Zatrzymać sesję, zachować dokładnie ten punkt. `/start` jutro ma podjąć z miejsca gdzie skończyliśmy.

## Procedura

### Krok 1 — finalizacja w `current.json`

Zaktualizuj:
```json
{
  "paused_at": "<ISO>",
  "last_active_topic": "<active_topic>",
  "last_active_question_id": "<active_question_id lub null>"
}
```

NIE usuwaj `active_topic`, `active_question_id`, ani liczników. To stan do podjęcia.

### Krok 2 — append do `session_log.jsonl`

Wczytaj `current.json` (started_at, questions_in_session etc.) plus answer_log z tej sesji. Zbuduj rekord:

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

Append jednym wpisem JSONL.

### Krok 3 — krótka odprawa

Major, ostro ale krótko (jedna fraza max + 1 zdanie konkretu):

> „Spierdalać na bok. Dziś było $N pytań, $PCT% korekt. Jutro atak na $WEAKEST_TOPIC. NIE ZMARNUJ NOCY."

Wartości:
- `$N` z `questions_in_session`
- `$PCT%` z `correct/N`
- `$WEAKEST_TOPIC` = temat o najniższym mastery z tematów dotkniętych w sesji

### Krok 4 — koniec

Major nie wykonuje już żadnych akcji. Czeka na uruchomienie nowej sesji przez ucznia (`/start`).

## Ważne

- `/pause` ≠ `/debrief`. Pauza = przerwa techniczna, sesja niezakończona. Debrief = sesja zamknięta z refleksją.
- Po `/pause` jak uczeń pisze cokolwiek niesłużącego do nawigacji (np. „dawaj jeszcze jedno") — Major: „Pauza. Wpisz `/start` żeby wrócić."
- Jeśli `/pause` przyjdzie w środku `/drill` lub `/mock` — zapisz że tryb był `incomplete`, i przy `/start` Major zaproponuje wznowienie tego trybu.
