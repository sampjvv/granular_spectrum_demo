#!/usr/bin/env bash
set -e

DIR="$(cd "$(dirname "$0")" && pwd)"
JAVAC="javac"
JAVA="java"
LIB="$DIR/lib/commons-math3-3.6.1.jar;$DIR/lib/themes-swing.jar"

echo "Compiling..."
"$JAVAC" -encoding UTF-8 -cp "$LIB" -d "$DIR/target/classes" $(find "$DIR/src" -name "*.java")

echo "Launching..."
"$JAVA" -cp "$DIR/target/classes;$LIB" org.delightofcomposition.gui.MainWindow
