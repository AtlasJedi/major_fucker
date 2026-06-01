# Pedagogy — why this system works

The Major's system is built on established principles from cognitive science and learning science literature. This document explains which ones, how they're implemented, and what they give you.

---

## 1. Active recall (testing effect)

**Principle:** attempting to retrieve information from memory is more effective for retention than re-reading the same information. The act of "pulling out" builds stronger neural pathways than "putting in".

**Implementation:**
- Major **always asks** before explaining anything.
- Model answer appears only AFTER your attempt.
- Even in `/lesson` mode, after the lecture there is ALWAYS a comprehension question — without it, the material won't stick.

**Source:** Karpicke, J. D., & Roediger, H. L. (2008). The critical importance of retrieval for learning. *Science, 319*(5865), 966-968.

> "Repeated retrieval enhances long-term retention more than repeated study."

In Karpicke and Roediger's experiments, students who tested themselves once remembered 80% of the material after a week. Students who read 4x — 35%. Active retrieval beat passive reading 2:1.

---

## 2. Spaced repetition

**Principle:** repetitions spread over time (1 day, 3 days, 7 days...) produce stronger retention than massed repetitions in one session ("cramming").

**Implementation:**
- Major uses the Leitner algorithm (3-box minimum):
  - A passed question returns after 1 session (box 1).
  - After successful review -> box 2 (3 sessions).
  - After another -> box 3 (7 sessions).
  - Failure drops back to box 1.
- The `due` field in `state/topics.json` tracks when to return to a topic.
- `/review` actively quizzes topics that are "due".

**Source:** Cepeda, N. J., Pashler, H., Vul, E., Wixted, J. T., & Rohrer, D. (2006). Distributed practice in verbal recall tasks. *Psychological Bulletin, 132*(3), 354-380.

Meta-analysis of 184 studies: the spacing effect is one of the most replicated effects in learning psychology. Optimal gap is approximately 10-30% of the interval to the planned test.

---

## 3. Mastery learning (Bloom)

**Principle:** the learner moves forward only when they've mastered the current material at a competency level (typically 80-90%). This eliminates "gaps in the foundation" that compound over years in traditional systems.

**Implementation:**
- Mastery threshold in the Major: **mastery score >= 0.85 AND at least 3 good answers at `apply` or `analyze` level in the last 5**.
- A topic is NOT marked as `mastered` until these conditions are met.
- Failed topics automatically return to `in_progress` after `/review`.

**Source:** Bloom, B. S. (1968). Learning for mastery. *Evaluation Comment, 1*(2), 1-12.

Bloom argued that the classical bell curve of learning outcomes (Gaussian distribution) reflects not differences in ability, but quality of instruction. With mastery learning, the distribution shifts right — most students achieve high levels.

**Next paper:** Bloom (1984) "2 Sigma Problem" — see below.

---

## 4. Bloom 2-sigma problem

**Principle:** students with an individual tutor achieve results 2 standard deviations higher than in a traditional 30-student classroom. This means: the average student with a tutor outperforms 98% of students in a classroom.

**Implementation:**
- Major is a simulation of an individual tutor.
- Every session is 1:1, tailored to your specific gaps.
- Difficulty dynamically regulated by mastery score.

**Source:** Bloom, B. S. (1984). The 2 Sigma Problem: The Search for Methods of Group Instruction as Effective as One-to-One Tutoring. *Educational Researcher, 13*(6), 4-16.

> "The most striking finding is that under the best learning conditions we can devise (tutoring), the average student is 2 sigma above the average control student."

Bloom called it the "2 Sigma Problem" because he was searching for ways to scale 1:1 tutoring quality to mass education. AI-driven tutors (like the Major) are one attempt at answering that question 40 years later.

---

## 5. Bloom's taxonomy (cognitive levels)

**Principle:** learning has levels. From lowest: remember -> understand -> apply -> analyze -> evaluate -> create. Questions at different levels train different skills.

**Implementation:**
- Every question in the Major's banks has a label: `recall` / `understand` / `apply` / `analyze`.
- Major selects the Bloom level appropriate to your mastery:
  - mastery < 0.3 -> `recall` (recognition / remembering definitions).
  - 0.3-0.6 -> `understand` (comprehension, paraphrasing).
  - 0.6-0.8 -> `apply` (application in a new context).
  - >= 0.8 -> `analyze` (comparison, evaluating trade-offs).

**Source:** Anderson, L. W., & Krathwohl, D. R. (Eds.). (2001). *A taxonomy for learning, teaching, and assessing: A revision of Bloom's taxonomy of educational objectives.* Longman.

(The original Bloom 1956 taxonomy was revised in 2001.)

---

## 6. Interleaving

**Principle:** mixing topics (interleaved practice) produces better retention and transfer than blocked practice ("learn Groovy on Friday, SQL on Saturday"). The brain forced to distinguish contexts builds stronger category understanding.

**Implementation:**
- Field `interleave_ratio: 0.2` in `state/topics.json` — every 5 questions Major throws in a question from a different topic.
- `/review` mode mixes topics by design.

**Source:** Rohrer, D., & Pashler, H. (2010). Recent research on human learning challenges conventional instructional strategies. *Educational Researcher, 39*(5), 406-412.

Classic math experiment: students doing blocked practice (10 problems type A, then 10 type B) performed worse on tests than interleaved (10 problems A and B mixed together).

---

## 7. Desirable difficulty

**Principle:** difficulty that requires effort but is achievable ("desirable difficulty") produces better learning than difficulty that's too low (boredom) or too high (frustration).

**Implementation:**
- Question difficulty regulated by mastery (section 5).
- Questions at the edge of ability — neither too easy (boredom) nor too hard (panic).
- In `/drill` mode, forced tempo adds pressure (desirable, similar to an actual interview).

**Source:** Bjork, R. A. (1994). Memory and metamemory considerations in the training of human beings. In J. Metcalfe & A. Shimamura (Eds.), *Metacognition: Knowing about knowing* (pp. 185-205). MIT Press.

---

## 8. Immediate feedback

**Principle:** feedback given immediately after an attempt builds stronger associations than delayed feedback. Longer delay -> the learner may even forget which answer was theirs.

**Implementation:**
- In `/drill` mode: feedback in 1-2 sentences immediately (saving tempo).
- In standard mode: full feedback (verdict + model answer + interview trap) immediately after the answer.
- Only `/mock` mode delays — because it simulates a real interview.

**Source:** Hattie, J., & Timperley, H. (2007). The power of feedback. *Review of Educational Research, 77*(1), 81-112.

Meta-analysis: immediate feedback has effect size d ~ 0.7 (large effect).

---

## 9. Fluency illusion alert

**Principle:** learners often confuse recognition ("this sounds familiar") with mastery ("I can explain this"). This is the "fluency illusion" — ease of reading = feeling of mastery, but actual retrieval from memory is hard.

**Implementation:**
- Major does NOT believe you when you say "I know this" / "that's obvious".
- Checks with an active question at `apply` level before letting it go.
- Second attempts after "I don't know" — to force an actual attempt.

**Source:** Koriat, A., & Bjork, R. A. (2005). Illusions of competence in monitoring one's knowledge during study. *Journal of Experimental Psychology: Learning, Memory, and Cognition, 31*(2), 187-194.

---

## 10. Persona / emotional encoding

**Principle:** material with stronger emotional valence (positive or negative) is better remembered. Classic "flashbulb memory" — you remember details of traumatic events better than neutral ones.

**Implementation:**
- The Hartman persona — screaming, blunt reprimands for mistakes — builds emotional engagement.
- Encountering an error under Hartman's pressure stays in memory better than plain text reading.

**Caveat:** too much stress hurts (Yerkes-Dodson law). That's why `/mock` turns off the persona (real test focus), and `/lesson` reduces it (instructional clarity). Persona is dosed, not constant.

**Source:** Cahill, L., & McGaugh, J. L. (1995). A novel demonstration of enhanced memory associated with emotional arousal. *Consciousness and Cognition, 4*(4), 410-421.

---

## What the system does NOT do (and why)

- **Multiple choice quizzes (ABCD)** — recognition != recall. Active retrieval requires open-ended questions.

- **Marathon sessions on one topic** — blocked practice loses to interleaved.

- **Generic feedback ("good job!")** — meaningless praise doesn't inform. Specific feedback ("nailed part X, missing Y") informs.

- **"Go back to the textbook"** — passive re-reading wastes time. Active testing wins.

- **"Passed, let's move on" on a weak answer** — mastery learning requires actual mastery.

---

## Bibliography

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

The Major is not a gimmick with a Hartman front. It's a tool built on replicated effects from the literature — testing effect, spacing effect, mastery learning, Bloom 2-sigma. If you use it according to the system (daily 30-60 min, `/debrief` at the end, `/review` weekly), the results will be better than classical reading + flashcards.
