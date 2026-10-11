#!/usr/bin/env bash
# Build and run EventFlow with nothing but a JDK (17 or newer).
#
#   ./run.sh test   compile everything and run the test suite
#   ./run.sh demo   compile and run Kenji's purchase story
set -euo pipefail
cd "$(dirname "$0")"

OUT=out
rm -rf "$OUT"
mkdir -p "$OUT/main" "$OUT/test"

# web/ (the presentation layer) needs Javalin, so it builds with Maven instead: mvn test
javac --release 17 -encoding UTF-8 -d "$OUT/main" $(find src/main/java -name '*.java' -not -path '*/web/*')

case "${1:-test}" in
  demo)
    java -cp "$OUT/main" eventflow.EventFlowDemo
    ;;
  test)
    javac --release 17 -encoding UTF-8 -cp "$OUT/main" -d "$OUT/test" $(find src/test/java -name '*.java' -not -path '*/web/*')
    java -cp "$OUT/main:$OUT/test" eventflow.TestRunner
    ;;
  *)
    echo "usage: ./run.sh [test|demo]" >&2
    exit 2
    ;;
esac
