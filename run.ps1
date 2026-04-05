$ErrorActionPreference = "Stop"

$dir = Split-Path -Parent $MyInvocation.MyCommand.Path
$javac = "javac"
$java = "java"
$jar = "jar"
$themesSwingDir = "C:\Users\sampe\Code\sptc\ui-lab\packages\themes-swing"
$lib = "$dir\lib\commons-math3-3.6.1.jar;$dir\lib\themes-swing.jar"

# Build themes-swing JAR
Write-Host "Building themes-swing..."
$themeSources = Get-ChildItem -Path "$themesSwingDir\src" -Recurse -Filter "*.java" | ForEach-Object { $_.FullName }
& $javac -encoding UTF-8 -d "$themesSwingDir\build\classes" @themeSources
& $jar cf "$themesSwingDir\build\themes-swing.jar" -C "$themesSwingDir\build\classes" .
Copy-Item "$themesSwingDir\build\themes-swing.jar" "$dir\lib\themes-swing.jar"

# Compile project
Write-Host "Compiling..."
$sources = Get-ChildItem -Path "$dir\src" -Recurse -Filter "*.java" | ForEach-Object { $_.FullName }
& $javac -encoding UTF-8 -cp $lib -d "$dir\target\classes" @sources

Write-Host "Launching..."
& $java -cp "$dir\target\classes;$lib" org.delightofcomposition.gui.MainWindow
