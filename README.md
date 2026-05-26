# major_fucker — twój prywatny instruktor

System agenta-tutora w Claude Code do nauki technologii pod presją rozmów rekrutacyjnych. Persona Sierżanta Hartmana z *Full Metal Jacket*, ale **pedagogika prawdziwa**: aktywne przypominanie, mastery learning, spaced repetition, immediate feedback.

---

## Jak zacząć

1. Otwórz tę reposytorię w Claude Code:
   ```
   cd ~/P/major_fucker
   claude
   ```

2. Wpisz w czacie:
   ```
   /start
   ```

3. Major przeprowadzi onboarding (5 pytań — 2-3 min) i odpali pierwsze pytanie z dyscyplinarnego tematu.

---

## Komendy

| Komenda | Co robi |
|---------|---------|
| `/start` | Rozpocznij lub wznów sesję. Onboarding przy pierwszym uruchomieniu. |
| `/next` | Pomiń aktualny temat, przeskocz do następnego. |
| `/knowledge` | Raport stanu wiedzy: tabela mastery per temat + readiness score. |
| `/more` | Dodaj nową technologię / temat do programu. Major sam zbuduje bank pytań. |
| `/drill` | Tryb intensywny: 10 pytań pod rząd, krótkie oceny, pełen feedback na końcu. |
| `/lesson` | Mini-wykład Majora 200-400 słów + pytanie sprawdzające. Persona zredukowana. |
| `/review` | Powtórka tematów dojrzałych do spaced repetition (Leitner 1/3/7 sesji). |
| `/mock` | Pełna symulacja rozmowy rekrutacyjnej 45-60 min. Major wyłącza personę. |
| `/status` | Snapshot bieżącej sesji (1 ekran, fakty). |
| `/pause` | Zachowaj stan, przerwij sesję. Wznawiasz `/start`. |
| `/debrief` | Strukturalna refleksja po sesji + plan na jutro. Zamyka sesję. |

---

## Co Major naprawdę robi

**Każde pytanie Major:**
1. Wybiera z banku tematu na poziomie Blooma odpowiednim do twojego mastery.
2. Zapisuje co zadał do `state/current.json`.
3. Czeka na twoją odpowiedź.
4. Ocenia: `correct` / `partial` / `correct_with_gap` / `incorrect`.
5. Pokazuje modelową odpowiedź (zawsze, niezależnie od oceny).
6. Aktualizuje twój mastery formułą EWMA (`new = 0.7×old + 0.3×score`).
7. Append do `state/answer_log.jsonl` i `state/topics.json`.

**Po sesji** (`/debrief`):
- Append do `state/session_log.jsonl` z metrykami.
- Update mastery, status tematów.
- Plan na jutro.

**Co kilka pytań** Major dorzuca pytanie z innego, już opanowanego tematu — interleaving (kontrola retencji).

---

## Stack startowy

System przygotowany pod konkretną rozmowę rekrutacyjną na **Software Engineer** ze stackiem **Groovy + SQL + REST + JSON/XML + HTML + OOP/Java** w platformie do zarządzania ceną (pricing engine).

| Temat | Priorytet | Pytań w banku | Pokrycie |
|-------|-----------|---------------|----------|
| Groovy | critical | 32 | składnia, closures, AST, MOP, Spock, Groovy 5.0 nowości |
| SQL | critical | 32 | JOIN, window functions, CTE, indexes, transactions, isolation |
| OOP / Java | high | 32 | SOLID, equals/hashCode, generics, streams, concurrency, records, Java 21 |
| REST API | high | 32 | metody, idempotencja, kody, JWT, OAuth2, paginacja, rate limiting |
| JSON / XML | normal | 32 | Jackson, JAXB, Slurper, parsing, walidacja, security (XXE, billion laughs) |
| Pricing domain | normal | 32 | waterfall, contracts, RGM, what-if, multi-currency, CPQ |
| HTML basics | low | 32 | semantic, forms, a11y, fetch, CORS, web components |

**224 pytania w banku startowym**, każde z modelową odpowiedzią + pułapką rozmowną.

---

## Dorzucanie nowych tematów

Wpisz `/more`, Major przeprowadzi cię przez:
1. Nazwa technologii (np. „Spring Security", „Kafka", „React Hooks").
2. Cel (rozmowa o pracę / projekt / ciekawość).
3. Samoocena 1-5.
4. Priorytet.

Major sam wypełni `content/topics/<slug>.md` bankiem pytań (min. 20 pytań × 4 poziomy Blooma).

---

## Pliki które tworzy / aktualizuje

```
state/
├── learner_profile.json    # twój profil, cel, deadline
├── topics.json             # mastery, status, due dates per temat
├── current.json            # co teraz robimy
├── answer_log.jsonl        # historia każdej odpowiedzi (append-only)
└── session_log.jsonl       # historia sesji (append-only)
```

---

## Filozofia

System jest oparty na sprawdzonych zasadach pedagogicznych. Patrz [`docs/pedagogy.md`](./docs/pedagogy.md) dla referencji (Karpicke & Roediger 2008, Bloom 2-sigma, mastery learning, Cepeda 2006).

**Major nie:**
- Nie chwali na zapas („dobre pytanie!", „świetna odpowiedź!").
- Nie podaje odpowiedzi przed twoją próbą.
- Nie pozwala udawać że umiesz.
- Nie wymyśla treści technicznych — bank pytań to `content/topics/*.md`.
- Nie zmyśla. Honesty rule: „TEGO NIE WIEM, ROBAKU, ALE ZARAZ ZWERYFIKUJĘ" jest OK; halucynacja nie.

---

## FAQ

**Q: Major krzyczy, ale to dla pedagogiki?**
A: Tak. Persona Hartmana to przyprawa zwiększająca pamięciowe zakorzenienie (emotional encoding). Merytoryka pod krzykiem jest precyzyjna i prawdziwa.

**Q: Co jeśli Major się myli?**
A: Popraw go. Major przyzna („Masz rację, robaku. Poprawiam bank.") i zaktualizuje `content/topics/<topic>.md`. Honesty over ego.

**Q: Mogę po angielsku?**
A: Domyślnie polski. Możesz prosić o angielski w `/start` (Major dostosuje frazowanie). Kod zawsze w oryginale.

**Q: Mogę używać poza prepem do rozmowy?**
A: Tak. `/more` dorzuca dowolny topic. System jest curriculum-agnostic.

**Q: Gdzie jest pełna dokumentacja struktury?**
A: [`docs/extending.md`](./docs/extending.md) — jak rozbudowywać; [`CLAUDE.md`](./CLAUDE.md) — twardy regulamin Majora.

---

## Licencja / kredyty

Personalny projekt Pawła. Persona inspirowana Sierżantem Hartmanem (R. Lee Ermey, *Full Metal Jacket* Stanley Kubricka 1987) — nie naruszająca jego pracy, tylko ducha musztry-pedagogiki. Treści techniczne z własnej wiedzy + research webowy.
