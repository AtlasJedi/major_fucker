#!/usr/bin/env bash
# Usage: ./scripts/log-and-update.sh <topic> <question_id> <level> <mode> <verdict> <score> "<notes>"
#   <level>   = junior | regular | senior | master   (the question's [level: ...] tag)
#   <mode>    = drill | code
#   <verdict> = correct | partial | correct_with_gap | incorrect
#   <score>   = 1.0 | 0.7 | 0.5 | 0.0
# Example: ./scripts/log-and-update.sh java_collections Q-JCOL-014 senior drill partial 0.5 "missed treeify threshold"
#
# What it does:
# 1. Appends a JSONL entry to state/answer_log.jsonl
# 2. Updates mastery in state/topics.json (EWMA: new = 0.7 * old + 0.3 * score)
#    - mode=drill -> mastery[<level>]   (theory ladder)
#    - mode=code  -> coding[<level>]    (coding ladder)
# 3. Bumps questions_asked, last_practice, flips queued -> in_progress

set -euo pipefail
cd "$(dirname "$0")/.."

TOPIC="$1"
QID="$2"
LEVEL="$3"         # junior | regular | senior | master
MODE="$4"          # drill | code
VERDICT="$5"
SCORE="$6"
NOTES="${7:-}"

TS=$(date -u +"%Y-%m-%dT%H:%M:%SZ")
TODAY=$(date -u +"%Y-%m-%d")

# Normalize: accept a bloom level and map it to a tier
case "$LEVEL" in
  recall)     LEVEL="junior" ;;
  understand) LEVEL="regular" ;;
  apply)      LEVEL="senior" ;;
  analyze)    LEVEL="master" ;;
esac

case "$LEVEL" in
  junior|regular|senior|master) : ;;
  *) echo "ERROR: level must be junior|regular|senior|master (got '$LEVEL')" >&2; exit 1 ;;
esac

LADDER="mastery"
[ "$MODE" = "code" ] && LADDER="coding"

# 1. Append to answer_log.jsonl
echo "{\"ts\":\"$TS\",\"topic\":\"$TOPIC\",\"question_id\":\"$QID\",\"level\":\"$LEVEL\",\"verdict\":\"$VERDICT\",\"score\":$SCORE,\"model_answer_shown\":true,\"notes\":\"$NOTES\",\"mode\":\"$MODE\",\"ladder\":\"$LADDER\"}" >> state/answer_log.jsonl

# 2. Update topics.json
python3 -c "
import json

topic  = '$TOPIC'
score  = float('$SCORE')
ladder = '$LADDER'
level  = '$LEVEL'
today  = '$TODAY'

with open('state/topics.json') as f:
    data = json.load(f)

t = data['topics'][topic]
slots = t[ladder]
slots[level] = round(0.7 * slots[level] + 0.3 * score, 3)

t['questions_asked'] = t.get('questions_asked', 0) + 1
t['last_practice'] = today
if t.get('status') in (None, 'queued'):
    t['status'] = 'in_progress'

with open('state/topics.json', 'w') as f:
    json.dump(data, f, indent=2)

print(f'{topic} | {ladder}.{level}: {slots[level]} | questions: {t[\"questions_asked\"]}')
"
