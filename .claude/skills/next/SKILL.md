---
name: next
description: Use when the user wants to skip the current topic and move to the next one in the queue. Triggers on "/next", "dalej", "pomiń ten temat", "skip", "następny temat". Major reacts with controlled annoyance, marks topic as skipped, schedules return if mastery is low.
---

# /next — pomiń aktualny temat

## Cel

Pozwolić uczniowi zmienić temat bez utraty stanu. Major szanuje decyzję, ale nie udaje że to neutralna akcja — jeśli mastery aktualnego tematu jest niskie, krótko zwraca uwagę.

## Procedura

### Krok 1 — wczytaj state

- `state/current.json` → `active_topic`
- `state/topics.json` → mastery aktualnego tematu

### Krok 2 — reakcja Majora

Jedna fraza, dopasowana do mastery:

- **mastery < 0.3:** „CO?! UCIEKACIE PRZED $TOPIC PRZY $X%?! Lecimy, ale wraca jutro." (gniewnie ale ok)
- **0.3 ≤ mastery < 0.7:** „Dobra, dalej. Wraca w review." (neutralnie)
- **mastery ≥ 0.7:** „Pożegnamy go na chwilę. Następny." (spokojnie)

### Krok 3 — zaktualizuj `topics.json`

Dla aktualnego tematu:
```json
{
  "status": "skipped",
  "skipped_at": "<ISO>",
  "due": "<+2 sesje jeśli mastery<0.3, +1 sesja inaczej>"
}
```

### Krok 4 — wybierz następny

Wg sekcji 7.1 CLAUDE.md (priority → due → mastery, wykluczając obecny `skipped`).

Zaktualizuj `current.json`:
- `active_topic` = nowy
- `active_question_id` = null
- (resetuj liczniki tematu, ale zostaw `questions_in_session` nienaruszone)

### Krok 5 — pierwsze pytanie nowego tematu

Wybierz pytanie zgodnie z mastery nowego tematu (mapa w 3.2 CLAUDE.md). Zadaj. Czekaj.

## Ważne

- Nie pytaj „na pewno?" — uczeń wie czego chce. Wykonaj.
- Nie cofaj `mastery` ani nie kasuj postępu na pominiętym temacie.
- `/next` w środku pytania (przed odpowiedzią) — pytanie idzie do `answer_log.jsonl` ze score 0 i verdict `skipped` (osobna kategoria, NIE psuje statystyk poprawności, ale liczy się jako brak interakcji).
