#!/usr/bin/env bash
# Stage 2: regenerates the updated class diagram and sequence diagrams and exports each .uxf to PNG with UMLet.
# The use-case diagram is unchanged from Stage 1 (docs/stage1/umlet).
# Usage: ./render.sh            (all)   ./render.sh sd2-monitoring-and-rules   (one)
set -euo pipefail
cd "$(dirname "$0")"
UMLET=../../../tools/umlet/umlet.jar
python3 gen_class_diagram.py >/dev/null
python3 gen_sequence_diagrams.py >/dev/null
for f in ${1:-*}.uxf; do
  [ -f "$f" ] || f="$1.uxf"
  java -jar "$UMLET" -action=convert -format=png -filename="$f" -output="${f%.uxf}" >/dev/null 2>&1
  echo "rendered ${f%.uxf}.png"
done
