#!/bin/sh
# Compiles ALL production classes against a real android.jar (API 33+) and hand-written
# Mapsforge/MapLibre API stubs (tests/compilestubs). This catches typos, missing methods and
# wrong argument lists in UI classes that the plain-Java tests cannot compile.
#   ANDROID_JAR=/pfad/zu/android-33/android.jar sh tests/compile-check.sh
set -eu
project_dir=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$project_dir"
: "${ANDROID_JAR:?ANDROID_JAR muss auf eine android.jar (API 33 oder neuer) zeigen}"
out=$(mktemp -d)
trap 'rm -rf "$out"' EXIT HUP INT TERM
java -m jdk.compiler/com.sun.tools.javac.Main -proc:none -Xlint:-options -d "$out" \
  -classpath "$ANDROID_JAR" \
  $(find tests/compilestubs -name '*.java') \
  $(find app/src/main/java -name '*.java')
echo "compile-check: alle $(find app/src/main/java -name '*.java' | wc -l) Produktionsklassen kompilieren gegen die Android-API"
