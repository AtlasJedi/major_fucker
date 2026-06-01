#!/usr/bin/env bash
# Usage: ./scripts/log-and-update.sh <topic> <question_id> <bloom_level> <mode> <verdict> <score> "<notes>"
# Example: ./scripts/log-and-update.sh kotlin CT-KT-B03 recall code correct 1.0 "all tests passed, idiomatic when used"
#
# What it does:
# 1. Appends a JSONL entry to state/answer_log.jsonl
# 2. Updates mastery in state/topics.json (EWMA: new = 0.7 * old + 0.3 * score)
# 3. Updates questions_asked and last_practice
# 4. Updates the appropriate mastery slot (theory/coding.junior/coding.mid/coding.senior)

set -euo pipefail
cd "$(dirname "$0")/.."

TOPIC="$1"
QID="$2"
BLOOM="$3"
MODE="$4"          # drill | code
VERDICT="$5"
SCORE="$6"
NOTES="${7:-}"

TS=$(date -u +"%Y-%m-%dT%H:%M:%SZ")
TODAY=$(date -u +"%Y-%m-%d")

# Determine mastery slot based on mode and bloom level
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

# 1. Append to answer_log.jsonl
echo "{\"ts\":\"$TS\",\"topic\":\"$TOPIC\",\"question_id\":\"$QID\",\"bloom_level\":\"$BLOOM\",\"my_answer\":\"see code\",\"verdict\":\"$VERDICT\",\"score\":$SCORE,\"model_answer_shown\":true,\"notes\":\"$NOTES\",\"mode\":\"$MODE\",\"slot\":\"$SLOT\"}" >> state/answer_log.jsonl

# 2. Update topics.json using python (available on macOS)
python3 -c "
import json, sys

topic = '$TOPIC'
score = float('$SCORE')
slot = '$SLOT'
today = '$TODAY'

with open('state/topics.json', 'r') as f:
    data = json.load(f)

t = data['topics'][topic]

# Migrate flat mastery to structured if needed
if isinstance(t.get('mastery'), (int, float)):
    old = t['mastery']
    t['mastery'] = {
        'theory': old,
        'coding': {'junior': 0.0, 'mid': 0.0, 'senior': 0.0}
    }

m = t['mastery']

# Update the right slot
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

# Print summary
print(f'{topic} | {slot}: {m if slot == \"theory\" else m[\"coding\"]} | questions: {t[\"questions_asked\"]}')
"
