---
name: drill
description: Use when the user wants intensive rapid-fire practice on the current topic. Triggers on "/drill", "drill", "szybkie pytania", "rapid fire", "seria pytań". Runs 10 quick questions with minimal feedback per question, then a consolidated review at the end.
---

# /drill — szybka seria 10 pytań

## Cel

Tryb intensywnego utrwalania. 10 pytań pod rząd z bieżącego tematu, krótka ocena per pytanie (✓/~/✗ + jedno-zdaniowa modelka), pełen feedback dopiero na końcu.

## Procedura

### Krok 1 — przygotuj sesję drill

Wczytaj `state/current.json`. Jeśli brak `active_topic` — uruchom `/start`.

Zapisz w `current.json`:
```json
{
  "drill_in_progress": true,
  "drill_started_at": "<ISO>",
  "drill_questions_planned": 10,
  "drill_questions_done": 0,
  "drill_results": []
}
```

Major komunikat:

> „DRILL. 10 pytań. Krótka ocena, modelka jedno zdanie, lecimy. Po wszystkim feedback. Gotów? STRZELAM."

### Krok 2 — pętla 10 pytań

Dla każdego pytania:

1. **Wybór:** poziom Blooma rozłożony — kiedy `mastery < 0.5`, mix recall/understand 7:3. Kiedy `0.5 ≤ mastery < 0.8`, mix understand/apply 6:4. Kiedy `mastery ≥ 0.8`, mix apply/analyze 6:4.
2. **Zadaj** — krótko, bez fraz Hartmana (oszczędzamy tempo). Tylko pytanie.
3. **Czekaj na odpowiedź ucznia.**
4. **Ocena natychmiastowa, bardzo krótka:**
   - `correct`: „✓ — Modelka: $JEDNO_ZDANIE."
   - `partial`: „~ — Brakuje: $LUKA. Modelka: $JEDNO_ZDANIE."
   - `incorrect`: „✗ — Modelka: $JEDNO_ZDANIE."
5. **Persistence:**
   - Append do `state/answer_log.jsonl` (jak normalnie).
   - Zaktualizuj `mastery` w `topics.json`.
   - Doklej do `current.json:drill_results` rekord: `{"q": "<id>", "score": <0-1>, "bloom": "<level>"}`.
   - Zwiększ `drill_questions_done`.
6. **Bez pułapki rozmownej w drillu** — to dla zachowania tempa. Pułapka wraca w finalnym debriefie.
7. **Następne pytanie natychmiast.** Bez „dalej?"-style przerywników.

### Krok 3 — finalny debrief drillu (po pytaniu 10)

```
DRILL ZAKOŃCZONY — $TOPIC

Wynik: $CORRECT/$TOTAL ($PCT%)
Breakdown po Bloom: recall X✓/Y, understand X✓/Y, apply X✓/Y, analyze X✓/Y

Mocne strony:
- $obserwacja_1
- $obserwacja_2

Słabe strony (do dorobienia):
- $luka_1 — patrz Q-XXX-NNN
- $luka_2

Następny krok:
- $sugestia (np. „lesson na $podtemat" / „kolejny drill" / „następny temat" / „/pause")
```

Update mastery delta — dorzuć do `current.json` finalny snapshot.

Wyłącz `drill_in_progress = false` w `current.json`.

Ostatnia fraza Hartmana: „SPOCZNIJ NA SEKUNDĘ. NASTĘPNY ROZKAZ?"

## Ważne

- Tempo > głębokość. Drill ma trenować recall pod presją, nie analitykę.
- Modelka per pytanie MA BYĆ JEDNO-ZDANIOWA. Pełne tłumaczenia odkładamy na koniec.
- Jeśli uczeń wpisze `/pause` w środku drillu — zapisz drill jako `incomplete`, daj zrobiony breakdown z dotychczasowych pytań.
- Drill nie powinien skoczyć w analyze jeśli mastery jest niskie — to demoralizuje. Reguła z kroku 2.1 obowiązuje.
