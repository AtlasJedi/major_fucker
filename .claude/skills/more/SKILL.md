---
name: more
description: Use when the user wants to add a new technology, framework, language, or topic to the curriculum. Triggers on "/more", "dodaj temat", "dorzuć technologię", "chcę się uczyć X", "add topic". Major asks 4 setup questions, generates a new topic file in content/topics/, populates it with question banks, and registers it in topics.json.
---

# /more — dodaj nowy temat do programu

## Cel

Rozszerzyć curriculum o nową technologię. Major **sam wypełnia** plik tematu — nie zostawia pustego szablonu, używa wiedzy własnej + ewentualnego web research.

## Procedura

### Krok 1 — wywiad (4 pytania, jedno po drugim)

> „Dorzucamy nowy temat do strzelania, robaku."

1. **Nazwa technologii / domeny** (np. „Kafka", „React Hooks", „Spring Security", „Domain-Driven Design")
2. **Cel:** rozmowa o pracę / projekt produkcyjny / ciekawość / certyfikacja
3. **Samoocena 1-5:** jak dobrze już to znasz
4. **Priorytet:** critical / high / normal / low

### Krok 2 — slug & ścieżka

Wygeneruj slug: lowercase, snake_case, ASCII-only.
- „Spring Security" → `spring_security`
- „React Hooks" → `react_hooks`
- „Domain-Driven Design" → `ddd`

Ścieżka pliku: `content/topics/<slug>.md`. Jeśli już istnieje — zapytaj „nadpisać czy uzupełnić?".

### Krok 3 — wygeneruj zawartość pliku

Major **wypełnia plik treścią z własnej wiedzy**. Jeśli ma narzędzie web search i temat jest świeży — robi research.

Minimum bazowe (NIE szablon — pełna treść):
- **5 pytań na każdy z 4 poziomów Bloom-a** (recall / understand / apply / analyze) = 20 pytań
- Każde pytanie ma: ID (`Q-<SLUG>-NNN`), modelową odpowiedź (5-15 zdań), pułapkę rozmowną, tagi
- Sekcja „Zakres" — bullet list 8-15 podtematów

Jeśli to temat krytyczny dla rozmowy (priority: critical/high) — Major dorzuca jeszcze 12 pytań żeby wyrównać do bazowych 32. Jeśli normal/low — 20 wystarczy.

Format wzorcowy (ZACHOWAJ):

```markdown
# <Technologia> — bank pytań

## Zakres

- bullet 1
- bullet 2
...

## Q-<SLUG>-001 [bloom: recall]
**Pytanie:** ...
**Modelowa odpowiedź:** ...
**Pułapka rozmowna:** ...
**Tagi:** ...

## Q-<SLUG>-002 [bloom: recall]
...
```

### Krok 4 — zarejestruj w `topics.json`

Dodaj wpis:
```json
"<slug>": {
  "priority": "<wybór z kroku 1.4>",
  "mastery": 0.0,
  "status": "queued",
  "due": null,
  "added_at": "<ISO>",
  "self_assessment": <1-5>
}
```

Dla samooceny 1-2 — bump priority +1 (jak w `/start`).

### Krok 5 — potwierdzenie

> „Dorzuciłem $TOPIC_NAME do listy strzelania. $N pytań w banku. Wracaj do `/start` albo dawaj `/start` żeby ruszyć."

## Ważne

- Plik tematu nie może zostać pusty ani z placeholderami typu „TODO". Ma działać od razu.
- Modelowe odpowiedzi muszą być merytoryczne, nie 1-zdaniowe filler-y.
- Jeśli Major nie ma pewnych informacji — dopisuje notatkę „[do weryfikacji $data]" przy konkretnym pytaniu, ale daje swoją najlepszą odpowiedź.
- Pytania z różnych poziomów Bloma muszą być prawdziwie różne — `recall` to fakt do przypomnienia, `apply` to zadanie/scenariusz, `analyze` to porównanie/ocena trade-offów.
