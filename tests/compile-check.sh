#!/bin/sh
# Compiles ALL production classes against a real android.jar (API 33+) plus small hand-written
# MapLibre/Osmosis API stubs (tests/compilestubs, tests/stubs/org). This catches typos, missing
# methods and wrong argument lists in UI classes that the plain-Java tests cannot compile.
# R and BuildConfig are generated from res/ and build.gradle for the check.
#   ANDROID_JAR=/pfad/zu/android-33/android.jar sh tests/compile-check.sh
set -eu
project_dir=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$project_dir"
: "${ANDROID_JAR:?ANDROID_JAR muss auf eine android.jar (API 33 oder neuer) zeigen}"
out=$(mktemp -d)
trap 'rm -rf "$out"' EXIT HUP INT TERM
gen="$out/gen/de/ronny/pololauncher"
mkdir -p "$gen"
version=$(sed -n "s/.*versionName '\([^']*\)'.*/\1/p" app/build.gradle | head -n 1)
printf 'package de.ronny.pololauncher;\npublic final class BuildConfig { public static final String VERSION_NAME = "%s"; public static final boolean DEBUG = false; }\n' "$version" > "$gen/BuildConfig.java"
{
  echo 'package de.ronny.pololauncher;'
  echo 'public final class R {'
  echo ' public static final class drawable {'
  n=0
  for f in app/src/main/res/drawable*/*; do
    n=$((n + 1)); name=$(basename "$f"); echo "  public static final int ${name%.*} = $n;"
  done
  echo ' }'
  echo ' public static final class mipmap { public static final int ic_launcher = 1; }'
  echo ' public static final class string { public static final int app_name = 1; }'
  echo ' public static final class xml { public static final int network_security_config = 1; }'
  echo ' public static final class style { public static final int AppTheme = 1; }'
  echo '}'
} > "$gen/R.java"
if ! java -m jdk.compiler/com.sun.tools.javac.Main -proc:none -Xlint:-options -d "$out/classes" \
  -classpath "$ANDROID_JAR" \
  $(find tests/compilestubs tests/stubs/org -name '*.java') \
  "$gen/BuildConfig.java" "$gen/R.java" \
  $(find app/src/main/java -name '*.java') > "$out/javac.log" 2>&1; then
  grep -v "Picked up" "$out/javac.log" >&2
  echo "compile-check: FEHLER - Kompilierung fehlgeschlagen" >&2
  exit 1
fi
echo "compile-check: alle $(find app/src/main/java -name '*.java' | wc -l) Produktionsklassen kompilieren gegen die Android-API"
