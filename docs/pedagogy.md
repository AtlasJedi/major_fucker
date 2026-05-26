# Pedagogika — czemu ten system działa

System Majora jest oparty na ustalonych zasadach z literatury cognitive science i learning science. Ten dokument tłumaczy które, jak są zaimplementowane, i co ci to daje.

---

## 1. Active recall (testing effect)

**Zasada:** próba wydobycia informacji z pamięci jest skuteczniejsza w utrwalaniu niż ponowne czytanie tej samej informacji. Akt „wyjmowania" buduje silniejsze ścieżki neuronowe niż „wkładanie".

**Implementacja:**
- Major **zawsze pyta** zanim cokolwiek wytłumaczy.
- Modelka pojawia się dopiero PO twojej próbie.
- Nawet w `/lesson` mode po wykładzie ZAWSZE jest pytanie sprawdzające — bez tego materiał się nie utrwali.

**Źródło:** Karpicke, J. D., & Roediger, H. L. (2008). The critical importance of retrieval for learning. *Science, 319*(5865), 966-968.

> "Repeated retrieval enhances long-term retention more than repeated study."

W eksperymentach Karpicke i Roediger studenci, którzy testowali się raz, pamiętali po tygodniu 80% materiału. Studenci którzy czytali 4× — 35%. Aktywne wydobywanie biło pasywne czytanie 2:1.

---

## 2. Spaced repetition

**Zasada:** powtórzenia rozłożone w czasie (1 dzień, 3 dni, 7 dni…) dają silniejsze utrwalenie niż masowe powtórzenia w jednej sesji ("cramming").

**Implementacja:**
- Major używa algorytmu Leitner (3-box minimum):
  - Pytanie zaliczone wraca po 1 sesji (box 1).
  - Po pomyślnej powtórce → box 2 (3 sesje).
  - Po kolejnej → box 3 (7 sesji).
  - Wpadka cofa do box 1.
- Pole `due` w `state/topics.json` śledzi kiedy wracać do tematu.
- `/review` aktywnie pyta o tematy "due".

**Źródło:** Cepeda, N. J., Pashler, H., Vul, E., Wixted, J. T., & Rohrer, D. (2006). Distributed practice in verbal recall tasks. *Psychological Bulletin, 132*(3), 354-380.

Meta-analiza 184 badań: spacing effect jest jednym z najbardziej replikowanych efektów w psychologii uczenia się. Optymalna przerwa ≈ 10-30% odstępu od planowanego testu.

---

## 3. Mastery learning (Bloom)

**Zasada:** uczeń idzie dalej dopiero kiedy opanował aktualny materiał na poziomie kompetencji (zazwyczaj 80-90%). To eliminuje "luki w fundamentach", które w klasycznym systemie kompounują się przez lata.

**Implementacja:**
- Próg mastery w Majorze: **mastery score ≥ 0.85 ORAZ minimum 3 dobre odpowiedzi na poziomie `apply` lub `analyze` z ostatnich 5**.
- Temat NIE oznaczony jako `mastered` dopóki te warunki nie są spełnione.
- Niezdolne tematy automatycznie wracają do `in_progress` po `/review`.

**Źródło:** Bloom, B. S. (1968). Learning for mastery. *Evaluation Comment, 1*(2), 1-12.

Bloom argumentował że klasyczne uczenie z normą rozkładu (Gaussa wyników) odzwierciedla nie różnice w zdolnościach, ale jakość instrukcji. Z mastery learning rozkład wyników przesuwa się w prawo — większość uczniów osiąga wysoki poziom.

**Następny papier:** Bloom (1984) "2 Sigma Problem" — patrz niżej.

---

## 4. Bloom 2-sigma problem

**Zasada:** uczniowie z indywidualnym tutorem osiągają wyniki 2 odchylenia standardowe wyższe niż w klasycznym systemie klasowym. To znaczy: średni uczeń z tutorem przewyższa 98% uczniów z klasy 30-osobowej.

**Implementacja:**
- Major to symulacja indywidualnego tutora.
- Każda sesja jest 1:1, dostosowana do twoich konkretnych luk.
- Trudność dynamicznie regulowana mastery score.

**Źródło:** Bloom, B. S. (1984). The 2 Sigma Problem: The Search for Methods of Group Instruction as Effective as One-to-One Tutoring. *Educational Researcher, 13*(6), 4-16.

> "The most striking finding is that under the best learning conditions we can devise (tutoring), the average student is 2 sigma above the average control student."

Bloom nazywa to "2 Sigma Problem" bo szuka jak skalować jakość 1:1 tutoringu na masową edukację. AI-driven tutory (jak Major) to jedna z prób odpowiedzi 40 lat później.

---

## 5. Bloom's taxonomy (poziomy poznawcze)

**Zasada:** uczenie się ma poziomy. Od najniższego: remember → understand → apply → analyze → evaluate → create. Pytania na różnych poziomach trenują różne umiejętności.

**Implementacja:**
- Każde pytanie w bankach Majora ma etykietę: `recall` / `understand` / `apply` / `analyze`.
- Major wybiera poziom Bloomu adekwatny do twojego mastery:
  - mastery < 0.3 → `recall` (recognition / przypomnienie definicji).
  - 0.3-0.6 → `understand` (zrozumienie, parafraza).
  - 0.6-0.8 → `apply` (zastosowanie w nowym kontekście).
  - ≥ 0.8 → `analyze` (porównanie, ocena trade-offów).

**Źródło:** Anderson, L. W., & Krathwohl, D. R. (Eds.). (2001). *A taxonomy for learning, teaching, and assessing: A revision of Bloom's taxonomy of educational objectives.* Longman.

(Oryginalna Bloom 1956 taksonomia została zrewidowana w 2001.)

---

## 6. Interleaving

**Zasada:** mieszanie tematów (interleaved practice) daje lepszą retencję i transfer niż blokowane praktykowanie (blocked practice — "naucz się Groovy w piątek, SQL w sobotę"). Mózg zmuszony do rozróżniania kontekstów buduje silniejsze rozumienie kategorii.

**Implementacja:**
- Pole `interleave_ratio: 0.2` w `state/topics.json` — co 5 pytań Major dorzuca pytanie z innego tematu.
- `/review` mode mieszane tematy z założenia.

**Źródło:** Rohrer, D., & Pashler, H. (2010). Recent research on human learning challenges conventional instructional strategies. *Educational Researcher, 39*(5), 406-412.

Klasyczny eksperyment z matematyką: studenci robiący blocked practice (10 problemów typu A, potem 10 typu B) wypadali gorzej na testach niż interleaved (10 problemów A i B przemieszanych).

---

## 7. Desirable difficulty

**Zasada:** trudność która wymaga wysiłku ale jest osiągalna ("desirable difficulty") daje lepsze uczenie się niż trudność za niska (banal) lub za wysoka (frustration).

**Implementacja:**
- Trudność pytań regulowana mastery (sekcja 5).
- Pytania na granicy możliwości — ani za łatwe (boredom), ani za trudne (panic).
- W trybie `/drill` wymuszone tempo dorzuca presji (pożądanej, podobnej do rozmowy rekrutacyjnej).

**Źródło:** Bjork, R. A. (1994). Memory and metamemory considerations in the training of human beings. In J. Metcalfe & A. Shimamura (Eds.), *Metacognition: Knowing about knowing* (pp. 185-205). MIT Press.

---

## 8. Immediate feedback

**Zasada:** feedback dany natychmiast po próbie buduje silniejsze associations niż feedback opóźniony. Dłuższe opóźnienie → uczeń może nawet zapomnieć która odpowiedź była jego.

**Implementacja:**
- W `/drill` mode: feedback w 1-2 zdaniach natychmiast (oszczędzasz tempo).
- W standard mode: pełen feedback (verdict + modelka + pułapka rozmowna) natychmiast po odpowiedzi.
- Tylko `/mock` mode opóźnia — bo symuluje prawdziwą rozmowę.

**Źródło:** Hattie, J., & Timperley, H. (2007). The power of feedback. *Review of Educational Research, 77*(1), 81-112.

Meta-analiza: immediate feedback ma effect size d ≈ 0.7 (duży efekt).

---

## 9. Fluency illusion alert

**Zasada:** uczeń często myli rozpoznanie (recognition: "to brzmi znajomo") z opanowaniem (recall: "umiem to wytłumaczyć"). To "fluency illusion" — łatwość przeczytania = poczucie mistrzostwa, ale faktyczne wydobycie z pamięci jest trudne.

**Implementacja:**
- Major NIE wierzy gdy mówisz "wiem to" / "to jasne".
- Sprawdzenie aktywnym pytaniem na poziomie `apply` zanim odpuści.
- Drugie próby po "nie wiem" — żeby wymusić rzeczywistą próbę.

**Źródło:** Koriat, A., & Bjork, R. A. (2005). Illusions of competence in monitoring one's knowledge during study. *Journal of Experimental Psychology: Learning, Memory, and Cognition, 31*(2), 187-194.

---

## 10. Persona / emotional encoding

**Zasada:** materiał z silniejszym emotional valence (pozytywnym lub negatywnym) jest lepiej zapamiętany. Klasyczny "flashbulb memory" — pamiętasz szczegóły traumatycznych wydarzeń lepiej niż neutralnych.

**Implementacja:**
- Persona Hartmana — krzyk, dosadne karcenie błędów — buduje emotional engagement.
- Spotkanie z błędem pod presją Hartmana zostaje pamiętane lepiej niż czyste czytanie tekstu.

**Caveat:** zbyt dużo stresu szkodzi (Yerkes-Dodson law). Stąd `/mock` wyłącza personę (real test focus), a `/lesson` ją redukuje (instructional clarity). Persona dawkowana, nie ciągła.

**Źródło:** Cahill, L., & McGaugh, J. L. (1995). A novel demonstration of enhanced memory associated with emotional arousal. *Consciousness and Cognition, 4*(4), 410-421.

---

## Co system NIE robi (i dlaczego)

❌ **Multiple choice quizes (ABCD)** — recognition ≠ recall. Aktywne wydobywanie wymaga otwartych pytań.

❌ **Marathon sessions w jednym temacie** — blocked practice traci na rzecz interleaved.

❌ **Generic feedback ("dobra robota!")** — bezsensownego pochwała nie informuje. Conkretny feedback („w punkt fragment X, brakuje Y") informuje.

❌ **„Wracaj do podręcznika"** — pasywne re-czytanie tracone czas. Aktywne testowanie wygrywa.

❌ **„Zaliczone, idziemy dalej" przy słabej odpowiedzi** — mastery learning wymaga rzeczywistego opanowania.

---

## Bibliografia

Anderson, L. W., & Krathwohl, D. R. (2001). *A taxonomy for learning, teaching, and assessing: A revision of Bloom's taxonomy.* Longman.

Bjork, R. A. (1994). Memory and metamemory considerations in the training of human beings. In *Metacognition: Knowing about knowing*.

Bloom, B. S. (1968). Learning for mastery. *Evaluation Comment, 1*(2), 1-12.

Bloom, B. S. (1984). The 2 Sigma Problem. *Educational Researcher, 13*(6), 4-16.

Cahill, L., & McGaugh, J. L. (1995). A novel demonstration of enhanced memory. *Consciousness and Cognition, 4*(4), 410-421.

Cepeda, N. J., Pashler, H., Vul, E., Wixted, J. T., & Rohrer, D. (2006). Distributed practice in verbal recall tasks. *Psychological Bulletin, 132*(3), 354-380.

Hattie, J., & Timperley, H. (2007). The power of feedback. *Review of Educational Research, 77*(1), 81-112.

Karpicke, J. D., & Roediger, H. L. (2008). The critical importance of retrieval for learning. *Science, 319*(5865), 966-968.

Koriat, A., & Bjork, R. A. (2005). Illusions of competence. *Journal of Experimental Psychology, 31*(2), 187-194.

Rohrer, D., & Pashler, H. (2010). Recent research on human learning challenges conventional instructional strategies. *Educational Researcher, 39*(5), 406-412.

---

## Bottom line

Major nie jest gimmick z czołowym Hartmanem. To narzędzie zbudowane na replikowanych efektach z literatury — testing effect, spacing effect, mastery learning, Bloom 2-sigma. Jeśli używasz go zgodnie z systemem (dziennie 30-60 min, `/debrief` na końcu, `/review` co tydzień), wyniki będą lepsze niż klasyczne czytanie + flashcardy.
