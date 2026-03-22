$ErrorActionPreference = "Stop"

$dir = Split-Path -Parent $MyInvocation.MyCommand.Path
$javac = "C:\Program Files\Microsoft\jdk-11.0.30.7-hotspot\bin\javac.exe"
$java = "C:\Program Files\Microsoft\jdk-11.0.30.7-hotspot\bin\java.exe"
$lib = "$dir\lib\commons-math3-3.6.1.jar"
$sources = Get-ChildItem -Path "$dir\src" -Recurse -Filter "*.java" | ForEach-Object { $_.FullName }

Write-Host "Compiling..."
& $javac -encoding UTF-8 -cp $lib -d "$dir\target\classes" @sources

Write-Host "Launching..."
& $java -cp "$dir\target\classes;$lib" org.delightofcomposition.gui.MainWindow
