---
name: knowledge
description: Use when the user wants to see the overall knowledge status across all topics. Triggers on "/knowledge", "co umiem", "stan wiedzy", "pokaż mi gdzie jestem", "raport", "knowledge status". Generates tabular report with mastery percentages, question stats, and Major's commentary.
---

# /knowledge — raport stanu wiedzy

## Cel

Pokazać uczniowi globalny obraz: gdzie jest opanowany, gdzie kuleje, ile zostało do „interview ready".

## Procedura

### Krok 1 — agregacja danych

Przeczytaj:
- `state/topics.json`
- `state/answer_log.jsonl` (cały, ale grupuj per topic)

Dla każdego tematu policz:
- `mastery_pct` = `mastery * 100`
- `questions_asked` = liczba wpisów w answer_log z tym topic
- `correct` = wpisy z verdict `correct`
- `partial` = wpisy z verdict `partial`
- `incorrect` = wpisy z verdict `incorrect`
- `last_practice` = max `ts` w answer_log dla tego topic
- `status` z topics.json

### Krok 2 — sformatuj tabelę

Markdown table, posortowane po mastery DESC (najlepsze na górze):

```
| Temat               | Mastery | Pytania | ✓ / ~ / ✗  | Ostatnia praktyka  | Status        |
|---------------------|---------|---------|-----------|-------------------|---------------|
| Groovy              |  84%    |   42    | 28 / 9 / 5 | 2026-05-06 18:42  | in_progress   |
| SQL                 |  61%    |   31    | 14 / 11 / 6| 2026-05-05 19:10  | in_progress   |
...
```

### Krok 3 — interview readiness score

Policz globalny readiness:

```
readiness = sum(priority_weight * mastery) / sum(priority_weight)
priority_weight: critical=4, high=3, normal=2, low=1
```

Klasyfikuj:
- `readiness ≥ 0.85` → **READY**
- `0.65 ≤ readiness < 0.85` → **CLOSE**
- `0.40 ≤ readiness < 0.65` → **NEEDS WORK**
- `< 0.40` → **NOT READY**

### Krok 4 — komentarz Majora

Dwie linijki, krótko, w persona, MERYTORYCZNIE:

> „Globalnie: $READINESS_LABEL ($READINESS_PCT%). Najlepiej stoisz na $TOP_TOPIC ($TOP%), najgorzej na $WORST_TOPIC ($WORST%). Następna sesja: drill $WORST_TOPIC."

Jedna fraza Hartmana na końcu („Pricewacie jak rekrut na pierwszej musztrze, ale Groovy zaczyna się rysować.") — i tylko jedna.

### Krok 5 — sugestia akcji

Skończ konkretną sugestią:
- Jeśli jest temat ze statusem `mastered` ale `due ≤ now` → „Czas na `/review`."
- Jeśli `interview_date` z `learner_profile.json` jest blisko (≤ 7 dni) i readiness < 0.65 → „Mock teraz, niezależnie od mastery."
- W przeciwnym razie → „Dalej drill na $WORST_TOPIC: `/start`."

## Ważne

- NIE zaczynaj drillu w tej komendzie. To raport, nie sesja.
- NIE krzycz tu tyle co w drillu. Tu jesteś instruktorem-analitykiem.
- Tabela ma być czytelna — wyrównaj kolumny spacjami albo użyj markdown table syntax.
