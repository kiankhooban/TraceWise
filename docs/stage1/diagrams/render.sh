#!/usr/bin/env bash
# Renders every diagram in src/ to PNG and SVG next to this script.
# Requires Java 21 and Graphviz (brew install graphviz). Downloads PlantUML on first run.
set -euo pipefail
python3 "$(dirname "$0")/tag_packages.py"
here="$(cd "$(dirname "$0")" && pwd)"
jar="$here/../../../tools/plantuml.jar"
version="1.2026.8"
if [ ! -f "$jar" ]; then
  mkdir -p "$(dirname "$jar")"
  curl -sL -o "$jar" "https://repo1.maven.org/maven2/net/sourceforge/plantuml/plantuml/$version/plantuml-$version.jar"
fi
java -DPLANTUML_LIMIT_SIZE=16384 -jar "$jar" -tpng -o "$here" "$here"/src/*.puml
java -DPLANTUML_LIMIT_SIZE=16384 -jar "$jar" -tsvg -o "$here" "$here"/src/*.puml
