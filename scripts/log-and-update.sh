#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."

TOPIC="$1"
QID="$2"
BLOOM="$3"
MODE="$4"
VERDICT="$5"
SCORE="$6"
NOTES="${7:-}"

TS=$(date -u +"%Y-%m-%dT%H:%M:%SZ")
TODAY=$(date -u +"%Y-%m-%d")

if [ "$MODE" = "drill" ]; then
    SLOT="theory"
elif [ "$MODE" = "code" ]; then
    case "$BLOOM" in
        recall|understand) SLOT="coding_junior" ;;
        apply)             SLOT="coding_mid" ;;
        analyze)           SLOT="coding_senior" ;;
        *)                 SLOT="coding_junior" ;;
    esac
else
    SLOT="theory"
fi

echo "{\"ts\":\"$TS\",\"topic\":\"$TOPIC\",\"question_id\":\"$QID\",\"bloom_level\":\"$BLOOM\",\"my_answer\":\"see code\",\"verdict\":\"$VERDICT\",\"score\":$SCORE,\"model_answer_shown\":true,\"notes\":\"$NOTES\",\"mode\":\"$MODE\",\"slot\":\"$SLOT\"}" >> state/answer_log.jsonl

python3 -c "
import json, sys

topic = '$TOPIC'
score = float('$SCORE')
slot = '$SLOT'
today = '$TODAY'

with open('state/topics.json', 'r') as f:
    data = json.load(f)

t = data['topics'][topic]

if isinstance(t.get('mastery'), (int, float)):
    old = t['mastery']
    t['mastery'] = {
        'theory': old,
        'coding': {'junior': 0.0, 'mid': 0.0, 'senior': 0.0}
    }

m = t['mastery']

if slot == 'theory':
    m['theory'] = round(0.7 * m['theory'] + 0.3 * score, 3)
elif slot == 'coding_junior':
    m['coding']['junior'] = round(0.7 * m['coding']['junior'] + 0.3 * score, 3)
elif slot == 'coding_mid':
    m['coding']['mid'] = round(0.7 * m['coding']['mid'] + 0.3 * score, 3)
elif slot == 'coding_senior':
    m['coding']['senior'] = round(0.7 * m['coding']['senior'] + 0.3 * score, 3)

t['questions_asked'] = t.get('questions_asked', 0) + 1
t['last_practice'] = today

with open('state/topics.json', 'w') as f:
    json.dump(data, f, indent=2)

print(f'{topic} | {slot}: {m if slot == \"theory\" else m[\"coding\"]} | questions: {t[\"questions_asked\"]}')
"
