# Rozszerzanie Majora

Jak dodawać nowe technologie / domeny / funkcje. Najczęściej wystarczy `/more` — Major sam zbuduje topic. Ten dokument pokrywa przypadki gdy chcesz zrobić to ręcznie albo dorzucić coś bardziej fundamentalnego.

---

## 1. Dodawanie nowego tematu (domyślnie: `/more`)

Najprostsza ścieżka. W sesji wpisz:

```
/more
```

Major przeprowadzi cię przez 4 pytania (nazwa, cel, samoocena, priorytet) i wygeneruje:
- Plik `content/topics/<slug>.md` z bankiem pytań (min. 20 × 4 poziomy Bloom-a).
- Wpis w `state/topics.json`.

Po `/more` możesz `/start` i Major włączy nowy temat do rotacji wg priorytetu.

---

## 2. Dodawanie tematu ręcznie

Gdy chcesz pełną kontrolę:

### Krok 1 — wybierz slug

Lowercase, snake_case, ASCII. Np. „React Hooks" → `react_hooks`.

### Krok 2 — utwórz `content/topics/<slug>.md`

Skopiuj strukturę z istniejącego pliku (np. `groovy.md`):

```markdown
# <Nazwa> — bank pytań

> Krótki kontekst (1-2 zdania): kiedy ten temat się przydaje, jaka domena.

## Zakres

- bullet list 8-15 podtematów

## Q-<SLUG>-001 [bloom: recall]
**Pytanie:** ...
**Modelowa odpowiedź:** ... (5-15 zdań)
**Pułapka rozmowna:** ... (2-3 zdania, klasyczne gotchas)
**Tagi:** tag1, tag2

## Q-<SLUG>-002 [bloom: recall]
...

## Q-<SLUG>-009 [bloom: understand]
...

## Q-<SLUG>-017 [bloom: apply]
...

## Q-<SLUG>-025 [bloom: analyze]
...
```

**Konwencje:**
- ID format: `Q-<SLUG_UPPER>-NNN` (3-cyfrowy zero-padded).
- Bloom levels w kolejności: recall (1-8) → understand (9-16) → apply (17-24) → analyze (25-32).
- Min. 32 pytania (8 per Bloom level) dla critical/high priority topics.
- Min. 20 pytań (5 per Bloom level) dla normal/low priority.

**Co stanowi dobre pytanie:**
- **Recall:** „co to jest X?", „wymień Y typy", „co robi Z funkcja".
- **Understand:** „wyjaśnij dlaczego X", „porównaj A i B", „co znaczy Y w kontekście Z".
- **Apply:** „napisz kod X", „zaprojektuj Y dla Z", „rozwiąż problem A używając B".
- **Analyze:** „porównaj trade-offy", „pokaż jak diagnozujesz problem X", „obroń decyzję Y vs Z".

**Co stanowi dobrą modelową odpowiedź:**
- 5-15 zdań (chyba że pytanie code-heavy — wtedy więcej).
- Zaczyna od klucza (point), potem detale.
- Konkretne przykłady (kod, formuła, analogia).
- Typowo zawiera punkt który rekruter chce usłyszeć.

**Co stanowi dobrą pułapkę rozmowną:**
- Klasyczne pomyłki kandydatów (mylenie A z B).
- Naïve odpowiedzi które brzmią dobrze ale są błędne.
- Follow-up pytania jakie rekruter zazwyczaj zadaje.
- 2-3 zdania, treściwie.

### Krok 3 — zarejestruj w `state/topics.json`

```json
{
  "topics": {
    ...,
    "react_hooks": {
      "priority": "high",      // critical | high | normal | low
      "mastery": 0.0,
      "status": "queued",
      "due": null,
      "questions_asked": 0,
      "last_practice": null
    }
  }
}
```

### Krok 4 — sprawdź że Major widzi temat

Wpisz `/knowledge` — temat powinien się pojawić w tabeli ze 0% mastery.

---

## 3. Dodawanie nowej komendy (skill)

Komendy to skille w `.claude/skills/<name>/SKILL.md`. Major automatycznie wykrywa.

### Krok 1 — utwórz `.claude/skills/<command>/SKILL.md`

```markdown
---
name: <command>
description: Use when ... (specific trigger phrases). Performs ...
---

# /<command> — krótki tytuł

## Cel

(1-2 zdania)

## Procedura

### Krok 1 — ...
### Krok 2 — ...

## Ważne
(uwagi, pułapki, edge cases)
```

**Wskazówki:**
- `description` musi być ostry, z konkretnymi trigger phrases — Claude Code używa go do dispatching. Patrz istniejące skille jako wzór.
- Sekcja „Ważne" zawiera anti-patterns i edge cases.
- Skill może (i powinien) odwoływać się do `state/*` plików, `content/*` plików, oraz innych skili.

### Krok 2 — uaktualnij listę w README.md

Tabela komend w README — dodaj wiersz dla nowej komendy.

### Krok 3 — uaktualnij `CLAUDE.md` jeśli komenda zmienia behaviour core

Jeśli skill modyfikuje fundamental flow (np. nowy tryb obok drill/lesson/mock), aktualizuj sekcję 3.4 w `CLAUDE.md`.

---

## 4. Dorzucanie fraz Hartmana

`content/persona/hartman_voice.md` ma kategorie z 30+ frazami każda. Żeby dorzucić więcej:

1. Otwórz plik.
2. Znajdź odpowiednią kategorię (powitania, pochwały, zganiania, przejścia, pauzy, specjalne).
3. Dorzuć nowe frazy zachowując numerację (lub przepisz numerację).
4. **Pamiętaj o twardym zakazie:** żadnych żartów z prawdziwych ułomności. Tylko z lenistwa intelektualnego, niedouczenia, fluency illusion.

---

## 5. Modyfikacja regułek pedagogicznych

Główny regulamin Majora to `CLAUDE.md`. Sekcje krytyczne:
- **Sekcja 2** — reguły pedagogiczne (mastery threshold, spaced repetition intervals, interleave ratio).
- **Sekcja 7** — mastery i kolejkowanie tematów.

**Przykładowa zmiana:** chcesz mniej agresywnego mastery threshold (z 0.85 na 0.80):

W `CLAUDE.md`:
- Sekcja 2.5 — zmień próg.
- Sekcja 7.3 — zmień warunek transition.

W skille:
- `start`, `next`, `review` — sprawdź czy odwołują się do progu i zaktualizuj.

---

## 6. Dorzucanie nowych poziomów Blooma

Domyślne poziomy: `recall`, `understand`, `apply`, `analyze`. Bloom 2001 ma jeszcze `evaluate` i `create`. Można dorzucić.

**Co trzeba zaktualizować:**
1. `CLAUDE.md` sekcja 2.2 — lista poziomów.
2. `CLAUDE.md` sekcja 3.2 — mapowanie mastery → poziom.
3. Skille (`drill`, `lesson`, `start`) — gdzie wybierany poziom.
4. Banki pytań — dodać pytania na nowych poziomach (lub oznaczyć istniejące).

**Praktycznie:** dla rozmów rekrutacyjnych 4 poziomy wystarczają. Evaluate/create przydatne dla creative roles, design interviews. Niekoniecznie Software Engineer.

---

## 7. Customization persona

Major to Hartman by default. Inne wzorce możliwe:

- **Profesor Snape** (zimny intelektualizm, sarcasm) — mniej krzyku, więcej cierpkich uwag.
- **Yoda** (stylized speech, philosophical) — niezbyt praktyczne dla intensive drill.
- **Spokojny mentor** (Mr. Miyagi style) — może być jak `/mock` mode permanent.

**Żeby zmienić personę:**
1. Przepisz `content/persona/hartman_voice.md` → `<persona_name>_voice.md`.
2. Zaktualizuj `CLAUDE.md` sekcja 1 (Tożsamość) — kim jest Major.
3. Skill plików zaktualizuj odwołania do persona file.

**Lepsze:** zachować Hartmana jako default, dodać `mode: persona=<name>` w `state/learner_profile.json` i load odpowiedni voice file conditionally.

---

## 8. Integracja z external tools

Major operuje wyłącznie na lokalnych plikach. Brak external API calls (poza opcjonalnym web search w `/more`). Jeśli chcesz integrować:

**Anki / SuperMemo (export pytań):**
- Wszystkie pytania w `content/topics/*.md` mogą być extracted skryptem (Markdown-aware parser) i export do CSV → Anki / Mochi.
- Frontmatter `[bloom: ...]` mapuje na decks / tags.

**LMS (np. Coursera, custom platform):**
- Skill może być rozszerzony o webhook do external LMS.
- API call po `/debrief` żeby zapisać sesję w external system.

**Speech-to-text (uczenie mówione):**
- Mock mode szczególnie nadaje się do practice z głosem.
- Whisper local + Major prompt → autentyczna mock interview experience.

**Te integracje wymagają custom code poza Majora — out of scope dla podstawowego setupu.**

---

## 9. Testowanie nowych skili

Po dorzuceniu skila:

1. **Smoke test:** wpisz triggerujący phrase, sprawdź że Major użyje skila (a nie inny / żaden).
2. **Edge cases:** brak danych (pusty `current.json`, no profile), invalid input, very large state files.
3. **Persistence test:** wykonaj akcję, sprawdź że state files są poprawnie zaktualizowane (JSON valid, nie corrupted).
4. **Persona test:** czy Major wciąż brzmi jak Hartman? Czy nie wszedł w meta-tryb?

---

## 10. Backup i versioning

System zapisuje state w `state/*` files. Recommendations:
- **Git** — `state/answer_log.jsonl` może być wzięty do gita (history nauki). Lub zaignorowany dla prywatności.
- **Backup** — przed major refactor (zmiana CLAUDE.md, mastery threshold), backup `state/*` żeby nie stracić progress.
- **Reset** — jeśli chcesz zacząć od zera: usuń `state/learner_profile.json` zawartość i `state/answer_log.jsonl` content. `state/topics.json` zresetuj do initial.

---

## 11. Najczęstsze problemy

**Major nie wybiera tematu:**
- `state/topics.json` ma wszystkie status `mastered` z `due` w przyszłości → nic do roboty.
- Solution: `/review` lub manualnie zresetuj statusy.

**Major powtarza te same pytania:**
- Problem dedup w `current.json`.
- Solution: sprawdź czy logika unikania ostatnich 30 z `answer_log.jsonl` działa.

**Mastery rośnie/spada za szybko:**
- Formuła EWMA `new = 0.7×old + 0.3×score` z alpha=0.3.
- Możesz przyjąć alpha=0.2 (bardziej smooth) edytując CLAUDE.md i skille.

**Persona wpływa na meritum:**
- Sygnał że Major dryfuje. Re-check `CLAUDE.md` sekcja 6 (Limit persony) — 1 fraza per wymiana max.

---

## Bottom line

System jest kompletny ale rozszerzalny. Najczęstszy use case (`/more`) załatwia 90% potrzeb. Bardziej zaawansowane modyfikacje (nowe komendy, persony, regulacje) wymagają edycji `CLAUDE.md` + skili, ale są w zasięgu — Major to ~3000 linii markdown total, czytalny ręcznie.
