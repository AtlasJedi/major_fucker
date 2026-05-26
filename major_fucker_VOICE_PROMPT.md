# PROMPT — dodaj voice mode do Major Fuckera (English)

> Ten prompt wkleisz do Claude Code w katalogu `~/projects/major_fucker/` (gdzie już zbudowałeś Majora). Używa **dwuetapowego workflow**: najpierw Opus projektuje, potem (po twojej akceptacji) Sonnet buduje. Etap 1 jest krótki bo decyzje są już podjęte — chodzi o weryfikację mojego systemu i drobne dopasowania.

---

## Kontekst

Mam już zbudowanego agenta `major_fucker` — instruktora-tutora w stylu Hartmana z *Full Metal Jacket*, który uczy mnie do rozmowy technicznej (Groovy/SQL/REST/JSON/XML/HTML/OOP/pricing). Działa w Claude Code, ma 11 skili (`/start`, `/next`, `/knowledge`, `/more`, `/drill`, `/lesson`, `/review`, `/mock`, `/status`, `/pause`, `/debrief`), trzyma state na dysku, banki pytań w `content/topics/`.

**Decyzja o języku voice mode'a: angielski.** Pasuje to do Hartmana z oryginału (z filmu) i upraszcza stack technologiczny do oficjalnego setupu Voice-Mode'a:

- **STT**: Whisper.cpp lokalnie (model `large-v3-turbo`).
- **TTS**: Kokoro lokalnie. Głos rekomendowany: **`am_adam`** (American male, low pitch ~116 Hz, authoritative) jako default Hartman; **`am_michael`** (warm, confident) jako fallback; **`af_bella`** lub **`af_nova`** jako kobiecy głos rekruterki w `/mock` mode.
- **Bridge**: VoiceMode MCP (`mbailey/voicemode`) — instaluje obie usługi automatycznie i oba mają OpenAI-compatible API od razu z pudełka. **Zero customu.**

To znaczy że pisanie z Majorem jest po polsku, ale komenda `/voice on` wprowadza tryb angielski (mówisz po angielsku, Major odpowiada po angielsku). To nie jest problem — Major umie po angielsku, persona Hartmana **i tak** jest po angielsku w oryginale.

---

# ETAP 1 — DESIGN (Opus)

> **Wymagam żeby ten etap zrobił model Opus.** Jeśli aktualnie jesteś Sonnet — przerwij i powiedz mi żebym przełączył (`/model opus`). Tu nie będzie dużo pisania, ale decyzje muszą być przemyślane (system audio, MCP integration, persona switching).

## Twoje zadania w ETAPIE 1

### 1. Audyt środowiska
Sprawdź **mój system** zanim cokolwiek zaprojektujesz:
- OS (Linux / macOS / Windows-WSL — pod Windows native VoiceMode nie działa, wymagany WSL2 z pulseaudio).
- Python ≥3.10.
- Czy `uv`/`uvx` zainstalowane.
- `ffmpeg`, `portaudio` (lub odpowiedniki).
- GPU obecne? (NVIDIA / Apple Silicon — wpływa na wybór modelu Whisper).
- Czy w `~/.claude.json` są już jakieś MCP entries.
- Lista urządzeń audio (mikrofon, speakers).
- Stan obecnego Majora w bieżącym katalogu (struktura, czy działa).

**NIE INSTALUJ jeszcze niczego.** Tylko zanotuj co trzeba doinstalować w ETAPIE 2.

### 2. Zaprojektuj integrację
Wyprodukuj **krótki** dokument `docs/voice-mode-design.md` zawierający:

#### a. Diagram architektury
```
[mic] → VoiceMode MCP → Whisper (localhost:2022) → text
                                                     ↓
                                     Major Fucker (Claude Code)
                                                     ↓
[speaker] ← VoiceMode MCP ← Kokoro (localhost:8880) ← text response
```

#### b. Decyzje techniczne (z krótkim uzasadnieniem)
- **Model Whisper**: rekomendowany `large-v3-turbo`. Jeśli mój sprzęt CPU-only i słaby — daj `medium.en`. Uzasadnij na bazie audytu.
- **Default głos Kokoro dla Hartmana**: `am_adam`. Sprawdź samples online (https://huggingface.co/hexgrad/Kokoro-82M) i potwierdź wybór, lub zaproponuj alternatywę z uzasadnieniem.
- **Speed Kokoro**: rekomenduję `1.1` lub `1.15` — naturalniejsza cadence dla wykrzykiwanego stylu sierżanta. Default Kokoro to 1.0.
- **Głos dla `/mock` rekrutera**: profesjonalna, neutralna kobieta lub mężczyzna. Rekomenduję `af_nova` lub `am_michael` (warm, professional). Speed 1.0.
- **Strategia uruchomienia**: rekomenduję `voicemode whisper service install` + `voicemode kokoro install` (oba z auto-startem przy starcie systemu jako systemd-user lub launchd, w zależności od OS-a).
- **VAD aggressiveness**: 3 (najbardziej agresywny silence detector — szybciej kończy nagrywanie kiedy kończysz mówić).
- **Silence threshold**: 1500ms (default-friendly).

#### c. Plan zmian w Major Fuckerze
- **Nowy skill** `.claude/skills/voice/SKILL.md`: toggle `/voice on` / `/voice off` / `/voice mock` / `/voice hartman`.
- **Modyfikacja `CLAUDE.md`** — dodaj sekcję **"Voice mode behavior"**:

  > Kiedy `state/current.json` ma `voice_mode: true`:
  > - Język odpowiedzi: **angielski**, niezależnie od języka pytania.
  > - Persona Hartmana W PEŁNI uruchomiona — jak w oryginalnym filmie (cytaty/styl: "What is your major malfunction, numbnuts?!", "Did your parents have any children that lived?!", "I am hard but I am fair").
  > - Max 50 słów per odpowiedź (≈25 sekund mówienia).
  > - **Zero markdown**, zero bloków kodu na głos.
  > - Kiedy pytanie wymaga pokazania kodu — Major mówi "EYES ON SCREEN, MAGGOT" i drukuje kod do tekstowego output (który nie zostanie odczytany ale ja zobaczę w terminalu).
  > - W trybie `/mock` voice mode — przełącz głos na `af_nova` lub `am_michael`, wyjdź z persony, mów neutralnie.

- **Modyfikacja banków pytań** — przejdź `content/topics/*.md` i otaguj pytania `requires_screen: true` jeśli wymagają wizualnej odpowiedzi (np. "napisz w Groovym closure...", "zaprojektuj schemat tabeli..."). W voice mode Major te pytania albo skipuje, albo prosi mnie o przejście do tekstu.

#### d. Plan instalacji (kroków)
Krótka lista, max 10 kroków:
```
1. Doinstaluj brakujące zależności systemowe (z audytu)
2. uvx voice-mode-install
3. claude mcp add --scope user voicemode -- uvx --refresh --with webrtcvad --with "setuptools<71" voice-mode
4. voicemode whisper install --model large-v3-turbo (lub mniejszy)
5. voicemode whisper service start (z autostartem)
6. voicemode kokoro install
7. voicemode kokoro start (z autostartem)
8. Konfiguracja env (głos, speed, VAD)
9. Permission w ~/.claude/settings.json
10. Smoke test
```

#### e. Risk register
Krótko:
- **macOS**: Terminal/iTerm potrzebuje uprawnień do mikrofonu w System Settings.
- **WSL2**: pulseaudio + libasound2-plugins wymagane, czasem flaky.
- **Echo bez słuchawek**: ostrzeż mnie że muszę używać headphones.
- **Hartmanowska persona w oryginale po angielsku ma wulgarne cytaty z filmu**: Major nie ma używać wulgarnych obelg z filmu (typu "shit-sandwich", "fucking" itd.) — pasuje stylistycznie ale nie chcę tego u siebie. Trzymaj się "maggot", "private", "numbnuts", "you are a disgrace" — bez f-bombów. Wymień to jasno w design dokumencie.
- **Latencja**: oczekiwana ~0.3s STT + Claude response time + ~0.5s TTS. Co tunować jeśli za wolne.

### 3. Pokaż design i CZEKAJ
Po napisaniu `docs/voice-mode-design.md` — pokaż mi go (cat lub krótkie podsumowanie 5-7 punktów). Zapytaj **"Akceptujesz design? Zmiany?"**

**NIE PRZECHODŹ DO ETAPU 2 BEZ MOJEJ AKCEPTACJI.**

---

# ETAP 2 — BUILD (Sonnet)

> Po akceptacji designu **przełącz model na Sonnet** (`/model sonnet`). Build to dużo tool calls (instalacja, edycja plików, smoke testy), Sonnet jest szybszy i tańszy.

## Wykonaj plan instalacji krok po kroku. Po każdym **kroku weryfikującym** pokaż output i potwierdź że ok przed dalej.

### Krok 1 — Zależności systemowe
Doinstaluj brakujące rzeczy z audytu (`uv`, `ffmpeg`, `portaudio`, `pulseaudio` na WSL). Użyj odpowiedniego package managera:
- macOS: `brew install ffmpeg portaudio`
- Linux: `apt install ffmpeg portaudio19-dev libasound2-dev` (Ubuntu/Debian)
- WSL2: dodatkowo `pulseaudio pulseaudio-utils libasound2-plugins`

Zainstaluj `uv` jeśli brakuje:
```bash
curl -LsSf https://astral.sh/uv/install.sh | sh
```

### Krok 2 — VoiceMode MCP
```bash
uvx voice-mode-install
claude mcp add --scope user voicemode -- uvx --refresh --with webrtcvad --with "setuptools<71" voice-mode
```
**Weryfikacja**: `uvx voice-mode --version` zwraca wersję; `~/.claude.json` zawiera entry `voicemode`.

### Krok 3 — Whisper STT lokalny
```bash
voicemode whisper install --model large-v3-turbo
voicemode whisper service install   # autostart
voicemode whisper service start
```
(Jeśli sprzęt słaby z designu — zmień model na `medium.en` lub `small.en`.)

**Weryfikacja**: `curl http://127.0.0.1:2022/v1/models` zwraca JSON z listą modeli.

### Krok 4 — Kokoro TTS lokalny
```bash
voicemode kokoro install
voicemode kokoro start
```
**Weryfikacja**: 
```bash
curl -X POST http://127.0.0.1:8880/v1/audio/speech \
  -H "Content-Type: application/json" \
  -d '{"model":"kokoro","voice":"am_adam","input":"Listen up maggot, this is your sergeant","response_format":"mp3","speed":1.15}' \
  --output /tmp/major.mp3
afplay /tmp/major.mp3   # macOS
# lub: mpv /tmp/major.mp3   # Linux
```
Powinieneś usłyszeć autorytatywny męski głos po angielsku.

### Krok 5 — Konfiguracja VoiceMode
Edytuj `~/.voicemode/voicemode.env` (utwórz jeśli nie istnieje):
```
VOICEMODE_STT_BASE_URLS=http://127.0.0.1:2022/v1
VOICEMODE_TTS_BASE_URLS=http://127.0.0.1:8880/v1
VOICEMODE_TTS_VOICE=am_adam
VOICEMODE_TTS_SPEED=1.15
VOICEMODE_VAD_AGGRESSIVENESS=3
VOICEMODE_SILENCE_THRESHOLD_MS=1500
VOICEMODE_DEFAULT_LISTEN_DURATION=30.0
```

Permissions w `~/.claude/settings.json`:
```json
{
  "permissions": {
    "allow": [
      "mcp__voicemode__converse",
      "mcp__voicemode__service"
    ]
  }
}
```

### Krok 6 — Skill `/voice` w Major Fuckerze
Stwórz `.claude/skills/voice/SKILL.md` z opisem zaprojektowanym w etapie 1. Sub-komendy:
- `/voice on` — aktywuje, ustawia `voice_mode: true` w `state/current.json`, anihiluje hartmanowski cytat ("YOUR EARS ARE ENGAGED, MAGGOT")
- `/voice off` — dezaktywuje
- `/voice mock` — przełącza głos na `af_nova` (lub `am_michael`), wychodzi z persony
- `/voice hartman` — wraca do `am_adam`, wraca persona

### Krok 7 — Modyfikacja `CLAUDE.md`
Dodaj sekcję **"Voice mode rules"** zaprojektowaną w etapie 1. Krytyczne:
- Sprawdzaj `state/current.json` na `voice_mode` przed każdą odpowiedzią.
- W voice mode: ang język, max 50 słów, zero markdown, zero kodu, persona Hartmana w pełni (ale BEZ wulgarnych cytatów z filmu — tylko "maggot", "private", "numbnuts", "disgrace", "step up").
- Pytania otagowane `requires_screen: true` w voice mode — Major mówi "THIS QUESTION NEEDS YOU AT THE SCREEN, MAGGOT — DROP TO TEXT MODE OR I SKIP IT" i daje wybór.

### Krok 8 — Tagowanie banków pytań
Przejdź `content/topics/*.md` i dla każdego pytania sprawdź czy wymaga ekranu. Dodaj `requires_screen: true` do pytań wymagających:
- Pisania kodu (np. "napisz closure...")
- Schematów / diagramów
- Czytania dłuższego kodu
Zostaw bez tego pytania konceptualne i krótkie definicyjne.

### Krok 9 — Dokumentacja użytkownika
`docs/voice-mode-usage.md`:
- Jak włączyć/wyłączyć
- Wymagania: słuchawki obowiązkowe (sprzężenie!)
- Lista głosów dostępnych i jak zmienić
- Troubleshooting: brak audio, mikrofon nie wykrywany, lag, restart serwisów
- Komenda do sprawdzenia statusu: `voicemode whisper service status` / `voicemode kokoro status`

`docs/voice-mode-uninstall.md`:
- Jak cofnąć wszystkie zmiany — odwrotne kroki, polecenia stop/uninstall serwisów, usunięcie wpisu z `~/.claude.json` i `~/.claude/settings.json`.

### Krok 10 — End-to-end smoke test
1. Restart Claude Code (`exit` i `claude` ponownie).
2. `/voice on` — Major potwierdza po hartmanowsku przez głośnik.
3. `/start` — Major zadaje pierwsze pytanie głosem.
4. Odpowiedź głosem (mów do mikrofonu).
5. Major ocenia + modelowa odpowiedź + następne pytanie — wszystko głosem.
6. Sprawdź interakcję z `requires_screen: true` pytaniem (Major powinien zaproponować skip/text).
7. `/mock` z aktywnym voice mode — głos zmienia się na rekruterski.
8. `/voice off` — Major potwierdza powrót do tekstu.
9. `cat state/answer_log.jsonl` — wpisy się dodały, niezależnie od trybu.

Pokaż mi że wszystko działa.

### Krok 11 — Sprzątanie
- Update głównego `README.md` projektu — sekcja "Voice mode" z linkiem do `docs/voice-mode-usage.md`.
- `tree` całego projektu — pokaż co jest gdzie.
- Hartmanowski cameo: jeśli wszystko działa, zostaw mi krótki message po angielsku w stylu Hartmana ("YOUR EARS ARE READY, PRIVATE. /start AND DON'T MAKE ME REPEAT MYSELF."). Jeśli coś nie działa — message bez persony, konkretnie co dalej.

---

## Zasady ogólne

1. **Język komunikacji ze mną**: polski. Komentarze w kodzie: angielski. Logi: jak zwracają.
2. **Bezpieczeństwo**: zanim coś instalujesz globalnie albo edytujesz `~/.claude.json` / system serwisy — pokaż mi co zrobisz. (Etap 1 nic nie instaluje.)
3. **Reverso**: każda zmiana systemowa ma odwrotną komendę w `docs/voice-mode-uninstall.md`.
4. **Honesty**: jeśli coś nie działa albo nie wiesz — powiedz. Nie zmyślaj outputu.
5. **Idempotency**: skrypty odporne na ponowne uruchomienie.

---

## Start

Etap 1 — **teraz**, jeśli jesteś Opusem. Po skończeniu projektu czekaj na akceptację. Po akceptacji i przełączeniu na Sonneta — etap 2.
