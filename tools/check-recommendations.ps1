param([Parameter(Mandatory=$true)][string]$Serial)
$ErrorActionPreference = 'Stop'
$repo = Split-Path $PSScriptRoot -Parent
$sdk = if($env:ANDROID_HOME){$env:ANDROID_HOME}else{$env:ANDROID_SDK_ROOT}
if(-not $sdk){throw 'Set ANDROID_HOME or ANDROID_SDK_ROOT.'}
$platform = Get-ChildItem -LiteralPath (Join-Path $sdk 'platforms') -Directory | Sort-Object Name | Select-Object -Last 1
$buildTools = Get-ChildItem -LiteralPath (Join-Path $sdk 'build-tools') -Directory | Sort-Object Name | Select-Object -Last 1
$androidJar = Join-Path $platform.FullName 'android.jar'
$out = Join-Path $repo 'out/recommendation-checks'
$classes = Join-Path $out 'classes'
$dex = Join-Path $out 'dex'
New-Item -ItemType Directory -Force $classes,$dex | Out-Null
$javac = Join-Path $env:JAVA_HOME 'bin/javac.exe'
$jar = Join-Path $env:JAVA_HOME 'bin/jar.exe'
$adb = Join-Path $sdk 'platform-tools/adb.exe'
& $javac -encoding UTF-8 -cp "$(Join-Path $repo 'out/classes');$androidJar" -d $classes (Join-Path $repo 'tests/recommendations/RecommendationChecks.java')
if($LASTEXITCODE -ne 0){throw 'Check compilation failed. Build the app first.'}
$classFiles = @(Get-ChildItem -LiteralPath $classes -Filter '*.class' -Recurse | ForEach-Object {$_.FullName})
& (Join-Path $buildTools.FullName 'd8.bat') --min-api 26 --lib $androidJar --output $dex (Join-Path $repo 'out/classes.dex') @classFiles
if($LASTEXITCODE -ne 0){throw 'D8 failed.'}
$archive = Join-Path $out 'checks.jar'
& $jar cf $archive -C $dex .
if($LASTEXITCODE -ne 0){throw 'Packaging checks failed.'}
& $adb -s $Serial push $archive /data/local/tmp/gsz-recommendation-checks.jar
if($LASTEXITCODE -ne 0){throw 'Could not copy checks to the emulator.'}
& $adb -s $Serial shell 'CLASSPATH=/data/local/tmp/gsz-recommendation-checks.jar app_process /system/bin com.gsztv.launcher.RecommendationChecks'
if($LASTEXITCODE -ne 0){throw 'Recommendation checks failed.'}
