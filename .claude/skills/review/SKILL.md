---
name: review
description: Use when the user wants to review previously mastered topics due for spaced repetition. Triggers on "/review", "powtórka", "odśwież", "spaced repetition", "co mi ucieka". Picks topics with status mastered and due ≤ now, runs mixed quiz across them, demotes topics that fail.
---

# /review — powtórka spaced repetition

## Cel

Sprawdzić retencję wcześniej opanowanych tematów. Mieszany quiz z tematów oznaczonych `mastered` ale o zaległej dacie `due`. Tematy które przejdą — awansują w Leitner (box +1, dłuższy interval). Tematy które się posypią — wracają do `in_progress`.

## Procedura

### Krok 1 — zidentyfikuj tematy do review

Wczytaj `state/topics.json`. Filter:
- `status == "mastered"`, ORAZ
- `due ≤ now` (gdzie `now` to bieżący timestamp ISO)

Posortuj rosnąco po `due` (najdawniej zaległe pierwsze).

Jeśli lista pusta:
> „Nic do powtórki, robaku. Wszystko świeże. Wracamy do drillu? (`/start`)"
Koniec procedury.

### Krok 2 — strategia review

- Liczba pytań: `min(total_review_due * 3, 15)` — 3 pytania na temat, max 15 łącznie.
- Mix poziomów Bloma: 30% understand, 40% apply, 30% analyze. Recall pomijamy w review (zbyt łatwe).
- Pytania interleavingowo (mieszać tematy, nie blokować).

Major:
> „REVIEW. $N tematów na celowniku, $K pytań. Pierdolne, jak coś zapomnieliście — wraca do drillu."

Przełącz `mode` w `current.json` na `review`. Ustaw:
```json
{
  "review_topics": ["t1", "t2", ...],
  "review_questions_planned": K,
  "review_questions_done": 0,
  "review_per_topic_results": {"t1": [], "t2": [], ...}
}
```

### Krok 3 — pętla pytań

Dla każdego pytania:

1. Wybierz temat round-robin z `review_topics`.
2. Wybierz pytanie z banku tematu na poziomie zgodnym z miksem (kontroluj liczniki).
3. Zadaj. Czekaj na odpowiedź.
4. Ocena + modelka + pułapka (pełny cykl, jak w drill mode w CLAUDE.md 3.2).
5. Persistence:
   - `answer_log.jsonl` standardowo.
   - W `current.json:review_per_topic_results[topic]` doklej `{"q":"<id>","score":<0-1>}`.
   - Mastery EWMA jak normalnie.

### Krok 4 — decyzja Leitner per temat (po wszystkich pytaniach)

Dla każdego tematu w `review_topics` policz `topic_review_score` = średnia ze score'ów.

- `topic_review_score ≥ 0.75` — **PASS**:
  - Pozostaw `status: mastered`.
  - Awansuj box: jeśli aktualny interval to +1 sesja, ustaw +3 sesje. Jeśli +3, ustaw +7. Jeśli już +7 — zostaw +7 (cap).
  - Update `due = now + new_interval`.

- `0.5 ≤ topic_review_score < 0.75` — **STAY**:
  - Zostaw `status: mastered`.
  - Box bez zmian, ale `due = now + 1 sesja` (cofnij do najmniejszego boxa).

- `topic_review_score < 0.5` — **FAIL**:
  - `status: in_progress` (DOWNGRADE).
  - `due: null`.
  - Zostaw mastery jakie jest (EWMA już go obniżyła pytaniami).
  - Major notuje to dosadnie w finalnym podsumowaniu.

### Krok 5 — finalny raport review

```
REVIEW ZAKOŃCZONY

Per temat:
- $TOPIC_1: $SCORE_PCT% — [PASS/STAY/FAIL] — następna powtórka: $DATE_OR_DROPPED_BACK
- ...

Globalnie: $OVERALL_SCORE_PCT%
Tematy zdegradowane do in_progress: [...] — wracają na musztrę.
Tematy umocnione (next box): [...] — wracają za 3/7 dni.
```

Wyjdź z `review mode` (przywróć `mode: drill` w current.json), wybierz następny temat wg sekcji 7.1 CLAUDE.md.

> „Tych co spadli — atakujemy jutro. Reszta na cooldown. NASTĘPNY?"

## Ważne

- Review NIE odpala `/start`-style onboarding. Zakłada że profil już istnieje.
- Pytania w review nie mogą być te same co w ostatniej sesji drill na tym temacie (dedup po `question_id` w ostatnich 30 wpisach answer_log).
- Jeśli temat ma <3 pytania w banku na wyznaczonych poziomach — Major bierze co ma plus dolne poziomy.
