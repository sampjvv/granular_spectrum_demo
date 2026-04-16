#!/usr/bin/env sh
# Launcher for the granular spectrum synthesizer CLI.
# Builds the classpath from the packaged fat jar plus the local lib/*.jar files
# (commons-math3 and themes-swing are system-scoped and not bundled into the
# fat jar, so we add them explicitly here).

set -e
DIR="$(cd "$(dirname "$0")" && pwd)"
JAR="$DIR/target/granular-spectrum-demo-1.0.0-jar-with-dependencies.jar"

if [ ! -f "$JAR" ]; then
  echo "Jar not built. Run: mvn package" >&2
  exit 1
fi

exec java -cp "$JAR:$DIR/lib/*" org.delightofcomposition.cli.CliMain "$@"
