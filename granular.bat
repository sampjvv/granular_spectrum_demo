@echo off
rem Launcher for the granular spectrum synthesizer CLI.
rem Builds the classpath from the packaged fat jar plus the local lib\*.jar files
rem (commons-math3 and themes-swing are system-scoped and not bundled into the
rem fat jar, so we add them explicitly here).

set DIR=%~dp0
set JAR=%DIR%target\granular-spectrum-demo-1.0.0-jar-with-dependencies.jar

if not exist "%JAR%" (
  echo Jar not built. Run: mvn package 1>&2
  exit /b 1
)

java -cp "%JAR%;%DIR%lib\*" org.delightofcomposition.cli.CliMain %*
