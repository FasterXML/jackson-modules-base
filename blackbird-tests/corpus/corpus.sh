#!/bin/bash
# Runs the jackson-databind test suite with Blackbird registered in every
# mapper, and compares the failures with expected-failures.txt. See
# ../README.md ("Databind test corpus").
#
# Usage: corpus.sh <databind-tree> <out-dir> [<blackbird-jar> | none]
#   databind-tree  a jackson-databind checkout with databind-register-blackbird.patch
#   out-dir        receives build.log, reports/ and failures.txt (emptied first)
#   blackbird-jar  default: the jar in blackbird/target of this checkout
#   none           runs the suite without Blackbird (the baseline)
# JAVA_HOME must be a JDK 25 or later. CORPUS_EXTRA_ARGS adds JVM arguments to
# the forked test JVM (for example -Dblackbird.debug.codegen=true).
# Exit status: 0 when the failures are exactly the expected ones, 1 when they
# differ, 2 or more when the run could not complete.
set -u
if [ $# -lt 2 ] || [ $# -gt 3 ]; then
  sed -n '6,10p' "$0" >&2
  exit 2
fi
HERE=$(cd "$(dirname "$0")" && pwd)
REPO=$(cd "$HERE/../.." && pwd)
TREE=$(cd "$1" && pwd) || exit 2
OUT=$2
JAR=${3:-}
if [ -z "$JAR" ]; then
  for f in "$REPO"/blackbird/target/jackson-module-blackbird-*.jar; do
    case "$f" in
      *-sources.jar|*-javadoc.jar|*-tests.jar) ;;
      *) [ -f "$f" ] && JAR=$f ;;
    esac
  done
  if [ -z "$JAR" ]; then
    echo "No Blackbird jar in $REPO/blackbird/target: run" >&2
    echo "  ./mvnw -B -pl blackbird -am package -DskipTests" >&2
    exit 2
  fi
fi
if ! grep -q 'blackbird.corpus' "$TREE/src/main/java/tools/jackson/databind/ObjectMapper.java"; then
  echo "$TREE does not have the corpus hook: in that tree, run" >&2
  echo "  git apply $HERE/databind-register-blackbird.patch" >&2
  exit 2
fi
rm -rf "$OUT"
mkdir -p "$OUT"
OUT=$(cd "$OUT" && pwd)

# The same opens that databind's own surefire configuration passes.
BASEARG="--add-opens=java.base/java.lang=tools.jackson.databind --add-opens=java.base/java.util=tools.jackson.databind --add-opens=tools.jackson.databind/tools.jackson.databind.jdk21=org.junit.platform.commons"
if [ "$JAR" = none ]; then
  EXTRA=("-Dcorpus.none=true")
  ARGLINE="$BASEARG"
else
  echo "Blackbird jar: $JAR"
  # Without module-info, the jar joins the test run as the automatic module
  # "blackbird.corpus". Databind's test descriptor leaves out three exports of
  # the real descriptor that Blackbird uses, so they are added here.
  python3 -I "$HERE/stripmod.py" "$JAR" "$OUT/blackbird-corpus.jar" || exit 3
  EXTRA=("-Dmaven.test.additionalClasspath=$OUT/blackbird-corpus.jar")
  EXPORTS=""
  for p in tools.jackson.databind.deser.impl tools.jackson.databind.ser.impl tools.jackson.databind.ext.javatime.ser.key; do
    EXPORTS="$EXPORTS --add-exports=tools.jackson.databind/$p=blackbird.corpus"
  done
  ARGLINE="$BASEARG $EXPORTS -Dblackbird.corpus=true -Dtools.jackson.module.blackbird.failOnCodegenError=true ${CORPUS_EXTRA_ARGS:-}"
fi
cd "$TREE" || exit 2
rm -rf target/surefire-reports
mvn -B -ntp test -Djacoco.skip=true \
  -Dmaven.test.failure.ignore=true \
  "${EXTRA[@]}" "-DargLine=$ARGLINE" > "$OUT/build.log" 2>&1
STATUS=$?
if [ $STATUS -ne 0 ] || [ ! -d target/surefire-reports ]; then
  echo "The databind build failed (exit $STATUS): see $OUT/build.log" >&2
  exit 3
fi
cp -r target/surefire-reports "$OUT/reports"
grep -E 'Tests run:.*Fail' "$OUT/build.log" | tail -1
python3 -I "$HERE/failures.py" "$OUT/reports" > "$OUT/failures.txt" || exit 3
tail -1 "$OUT/failures.txt"

if [ "$JAR" = none ]; then
  EXPECTED=/dev/null
else
  EXPECTED="$HERE/expected-failures.txt"
fi
grep -E '^[FE] ' "$OUT/failures.txt" | awk '{print $2}' | sort > "$OUT/failed-names.txt"
grep -v -E '^#|^$' "$EXPECTED" | awk '{print $1}' | sort > "$OUT/expected-names.txt"
UNEXPECTED=$(comm -23 "$OUT/failed-names.txt" "$OUT/expected-names.txt")
MISSING=$(comm -13 "$OUT/failed-names.txt" "$OUT/expected-names.txt")
if [ -z "$UNEXPECTED" ] && [ -z "$MISSING" ]; then
  echo "Result: only the expected failures."
  exit 0
fi
if [ -n "$UNEXPECTED" ]; then
  echo "Unexpected failures (details in $OUT/failures.txt):"
  echo "$UNEXPECTED"
fi
if [ -n "$MISSING" ]; then
  echo "Expected failures that did not fail (update expected-failures.txt):"
  echo "$MISSING"
fi
exit 1
