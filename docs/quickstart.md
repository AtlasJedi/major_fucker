# Quick Start Guide

Step-by-step walkthrough from clone to first drill question.

## 1. Clone and open

```bash
git clone https://github.com/YOUR_USER/major_fucker.git
cd major_fucker
claude
```

## 2. Onboard with CV + job posting

```
/onboard
```

Major asks for two things:
1. **Your CV** — paste the text directly, or give a file path (PDF/MD supported)
2. **The job posting** — paste the full text of the position you're targeting

## 3. Review gap analysis

Major analyzes both documents and shows a table:

```
 # | Temat              | Priorytet | Powód
---|--------------------|-----------|-----------------------------------------
 1 | Kotlin             | CRITICAL  | Wymagane w ofercie, brak na CV
 2 | Spring WebFlux     | CRITICAL  | "must have" w ofercie
 3 | SQL                | NORMAL    | Na CV, ale posting wymaga zaawansowane
```

Type topic numbers to select (e.g., `1,2,3`) or `all` for everything.

## 4. Answer setup questions

Major asks:
- When is your interview? (date or "nie wiem")
- Minutes per session? (default: 60)
- Language: Polish or English?

## 5. Start drilling

```
/start
```

Major picks the highest-priority topic and fires the first question at a Bloom level matching your current mastery (starts at `recall` for new topics).

## 6. Answer and get feedback

Type your answer. Major scores it:
- **correct** (1.0) — full marks
- **correct_with_gap** (0.7) — right idea, missing details
- **partial** (0.5) — on track but incomplete
- **incorrect** (0.0) — wrong or empty

Then shows the model answer (always), plus any interview traps.

## 7. Keep going

Major automatically selects the next question, gradually increasing difficulty as your mastery grows. Every ~5 questions, an interleaving question from another topic tests retention.

## 8. Pause and resume

```
/pause          # save state, come back later
/start          # resume where you left off
```

## 9. Other modes

```
/drill          # rapid-fire: 10 questions, minimal feedback, full debrief at end
/code           # coding exercises in playground/ with real tests
/lesson         # mini-lecture on your weakest subtopic + comprehension check
/mock           # full mock interview simulation (persona OFF, 45-60 min)
```

## 10. End of session

```
/debrief        # structured reflection, session metrics, plan for next time
```

## 11. Customize later

```
/config         # change topics, priorities, interview date, language
/more           # add a topic manually without re-running onboarding
/knowledge      # mastery report across all topics
```
