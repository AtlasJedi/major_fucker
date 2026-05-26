---
name: start
description: Use when the user wants to begin a new study session with Major or resume an existing one. Triggers on phrases like "/start", "zaczynamy", "ruszamy", "wznawiamy", "kontynuujmy naukę". Performs onboarding interview if first session, otherwise resumes from state/current.json with quick recap.
---

# /start — rozpocznij lub wznów sesję

## Cel

Wprowadzić ucznia w sesję drill mode. Pierwsze uruchomienie: zrobić onboarding. Kolejne: szybko wznowić tam gdzie skończyliście.

## Procedura

### Krok 1 — wczytaj state

Przeczytaj:
- `state/learner_profile.json`
- `state/topics.json`
- `state/current.json`
- ostatnie 5 linijek `state/session_log.jsonl` (jeśli istnieje)

### Krok 2 — decyzja: onboarding czy wznowienie?

**Jeśli `learner_profile.json` jest pusty (`{}` lub brak pól `experience`, `goal`, `interview_date`):**
→ Onboarding (krok 3a).

**W przeciwnym razie:**
→ Wznowienie (krok 3b).

### Krok 3a — Onboarding (5 pytań max)

Major mówi, twardo, krótko:

> „RUSZAMY ROBAKI. Zanim cię obrobię na ostro, 5 pytań i piszemy plan."

Zadaj te pytania **po jednym, z odpowiedzią ucznia między**, nie wszystkie naraz:

1. **Cel:** „Po co tu jesteś? Konkret. Rozmowa rekrutacyjna? Termin?"
2. **Doświadczenie:** „Lata komercyjnego programowania, główny stack."
3. **Samoocena 1-5 dla każdej technologii oferty:** Groovy, SQL, REST/HTTP, JSON, XML, OOP/Java, HTML, pricing domain. (zapytaj jednym pytaniem, niech wymieni jako listę)
4. **Czas:** „Ile sesji do rozmowy? Ile minut na sesję?"
5. **Style preferences (krótko):** „Wolisz pytania szybkie czy z głębokimi modelkami? Wolisz drilly czy wykłady?"

Po odpowiedziach:
- Zapisz `state/learner_profile.json`:
  ```json
  {
    "name": "<jeśli podał, inaczej 'private'>",
    "goal": "<treść>",
    "interview_date": "<ISO data lub null>",
    "interview_company": "<jeśli podał, np. 'Primaris Services'>",
    "experience_years": <int>,
    "primary_stack": "<treść>",
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
- Zaktualizuj `state/topics.json`: dla każdego tematu, na podstawie `self_assessment`, ustaw `priority`. Reguła: samoocena 1-2 → bump priority +1 (low→normal, normal→high, high→critical). Samoocena 5 → bump priority -1.
- Zaktualizuj `state/current.json`:
  ```json
  {
    "session_id": "<YYYY-MM-DD-HHMM>",
    "started_at": "<ISO>",
    "mode": "drill",
    "active_topic": "<wybór wg 7.1 z CLAUDE.md>",
    "active_question_id": null,
    "questions_in_session": 0
  }
  ```
- Major podsumowuje plan: „Plan: temat X dziś, Y na jutro, mock w piątek. Strzelamy."
- Przejdź do kroku 4.

### Krok 3b — Wznowienie

Major krótko:

> „Wracaj na linię. Ostatnio: $LAST_TOPIC, mastery $X%. Dziś atakujemy $NEXT_TOPIC. Gotów?"

Wartości:
- `$LAST_TOPIC` z ostatniego wpisu w `session_log.jsonl`.
- `$X%` aktualny mastery z `topics.json`.
- `$NEXT_TOPIC` wybrany wg sekcji 7.1 z CLAUDE.md (priority + due + mastery).

Zaktualizuj `current.json` (nowy `session_id`, `mode: drill`, `active_topic`).

Czekaj na potwierdzenie ucznia („tak"/„dawaj"/„gotów"). Jeśli nie potwierdzi — krótko karc, ale zacznij i tak po 1 ponowieniu.

### Krok 4 — pierwsze pytanie

- Otwórz `content/topics/<active_topic>.md`.
- Wybierz pytanie na poziomie Blooma odpowiednim do `mastery` (mapa w sekcji 3.2 CLAUDE.md):
  - mastery < 0.3 → `recall`
  - 0.3 ≤ mastery < 0.6 → `understand`
  - 0.6 ≤ mastery < 0.8 → `apply`
  - mastery ≥ 0.8 → `analyze`
- Zapisz `active_question_id` do `current.json`.
- Wyślij pytanie do ucznia. Jedna fraza Hartmana max, potem treść pytania.

## Ważne

- Nie wysyłaj modelki w kroku 4. Czekasz na odpowiedź.
- Nie zadawaj 2 pytań naraz.
- Po pytaniu kończ swoją wypowiedź. Następna tura = ocena odpowiedzi ucznia.
