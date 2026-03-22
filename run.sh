#!/usr/bin/env bash
set -e

DIR="$(cd "$(dirname "$0")" && pwd)"
JAVA_HOME="/c/Program Files/Microsoft/jdk-11.0.30.7-hotspot"
JAVAC="$JAVA_HOME/bin/javac"
JAVA="$JAVA_HOME/bin/java"
LIB="$DIR/lib/commons-math3-3.6.1.jar"

echo "Compiling..."
"$JAVAC" -encoding UTF-8 -cp "$LIB" -d "$DIR/target/classes" $(find "$DIR/src" -name "*.java")

echo "Launching..."
"$JAVA" -cp "$DIR/target/classes;$LIB" org.delightofcomposition.gui.MainWindow
