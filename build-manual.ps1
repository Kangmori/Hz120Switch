#requires -Version 5.1
<#
Builds Hz120Switch without Gradle, using the Android SDK command line tools
that Android Studio installed, plus the Shizuku AARs in .\libs.

Output: build-manual\Hz120Switch.apk

Usage:
    powershell -ExecutionPolicy Bypass -File .\build-manual.ps1
#>
$ErrorActionPreference = 'Stop'

$sdk = if ($env:ANDROID_SDK_ROOT) { $env:ANDROID_SDK_ROOT }
       elseif ($env:ANDROID_HOME) { $env:ANDROID_HOME }
       else { Join-Path $env:LOCALAPPDATA 'Android\Sdk' }

$buildTools = Join-Path $sdk 'build-tools\36.0.0'
$platform = Join-Path $sdk 'platforms\android-37.0'
$androidJar = Join-Path $platform 'android.jar'
$frameworkAidl = Join-Path $platform 'framework.aidl'
$javaHome = 'C:\Program Files\Android\Android Studio\jbr'

# Download the Shizuku AARs on first build (they are git-ignored).
$libsDir = Join-Path $PSScriptRoot 'libs'
$aarSources = @{
    'shizuku-api'      = 'https://repo1.maven.org/maven2/dev/rikka/shizuku/api/13.1.5/api-13.1.5.aar'
    'shizuku-provider' = 'https://repo1.maven.org/maven2/dev/rikka/shizuku/provider/13.1.5/provider-13.1.5.aar'
    'shizuku-aidl'     = 'https://repo1.maven.org/maven2/dev/rikka/shizuku/aidl/13.1.5/aidl-13.1.5.aar'
    'shizuku-shared'   = 'https://repo1.maven.org/maven2/dev/rikka/shizuku/shared/13.1.5/shared-13.1.5.aar'
}
foreach ($name in $aarSources.Keys) {
    $jar = Join-Path $libsDir "$name\classes.jar"
    if (Test-Path $jar) { continue }
    $aar = Join-Path $libsDir "$name.aar"
    New-Item -ItemType Directory -Force -Path $libsDir | Out-Null
    Invoke-WebRequest -Uri $aarSources[$name] -OutFile $aar -UseBasicParsing
    Expand-Archive -Path $aar -DestinationPath (Join-Path $libsDir $name) -Force
}
$annJar = Join-Path $libsDir 'annotation.jar'
if (-not (Test-Path $annJar)) {
    New-Item -ItemType Directory -Force -Path $libsDir | Out-Null
    Invoke-WebRequest -Uri 'https://dl.google.com/dl/android/maven2/androidx/annotation/annotation/1.9.1/annotation-1.9.1.jar' -OutFile $annJar -UseBasicParsing
}
$apiJar = Join-Path $PSScriptRoot 'libs\shizuku-api\classes.jar'
$aidlJar = Join-Path $PSScriptRoot 'libs\shizuku-aidl\classes.jar'
$sharedJar = Join-Path $PSScriptRoot 'libs\shizuku-shared\classes.jar'
$providerJar = Join-Path $PSScriptRoot 'libs\shizuku-provider\classes.jar'
$annotationJar = Join-Path $PSScriptRoot 'libs\annotation.jar'

foreach ($required in @($buildTools, $androidJar, $frameworkAidl, $javaHome, $apiJar, $aidlJar, $sharedJar, $providerJar)) {
    if (-not (Test-Path $required)) { throw "Missing: $required" }
}

$env:JAVA_HOME = $javaHome
$env:PATH = (Join-Path $javaHome 'bin') + ';' + $env:PATH

$project = $PSScriptRoot
$main = Join-Path $project 'app\src\main'
$out = Join-Path $project 'build-manual'

if (Test-Path $out) { Remove-Item $out -Recurse -Force }
New-Item -ItemType Directory -Path $out | Out-Null
New-Item -ItemType Directory -Path (Join-Path $out 'classes') | Out-Null
New-Item -ItemType Directory -Path (Join-Path $out 'dex') | Out-Null
New-Item -ItemType Directory -Path (Join-Path $out 'gen') | Out-Null

$manifest = [System.IO.File]::ReadAllText((Join-Path $main 'AndroidManifest.xml'),
    (New-Object System.Text.UTF8Encoding($false)))
$manifest = $manifest -replace '<manifest ', '<manifest package="com.dsh.hz120" '
$manifestPath = Join-Path $out 'AndroidManifest.xml'
[System.IO.File]::WriteAllText($manifestPath, $manifest, (New-Object System.Text.UTF8Encoding($false)))

Write-Host '[1/8] aapt2 compile'
$resDir = Join-Path $main 'res'
$resZip = Join-Path $out 'res.zip'
if (Test-Path $resDir) {
    & (Join-Path $buildTools 'aapt2.exe') compile --dir $resDir -o $resZip
    if ($LASTEXITCODE -ne 0) { throw 'aapt2 compile failed' }
}

Write-Host '[2/8] aapt2 link'
$baseApk = Join-Path $out 'base.apk'
$aapt2Args = @(
    'link',
    '-o', $baseApk,
    '-I', $androidJar,
    '--manifest', $manifestPath,
    '--min-sdk-version', '26',
    '--target-sdk-version', '36',
    '--version-code', '1',
    '--version-name', '1.0',
    '--auto-add-overlay',
    '--java', (Join-Path $out 'gen')
)
if (Test-Path $resZip) {
    $aapt2Args += @('-R', $resZip)
}
& (Join-Path $buildTools 'aapt2.exe') @aapt2Args
if ($LASTEXITCODE -ne 0) { throw 'aapt2 link failed' }

Write-Host '[3/8] aidl'
$aidlFile = Join-Path $main 'aidl\com\dsh\hz120\IUserService.aidl'
$aIdlArgs = @(
    "-p$frameworkAidl",
    "-I$(Join-Path $main 'aidl')",
    "-o$(Join-Path $out 'gen')",
    $aidlFile
)
& (Join-Path $buildTools 'aidl.exe') @aIdlArgs
if ($LASTEXITCODE -ne 0) { throw 'aidl failed' }

Write-Host '[4/8] javac'
$classpath = @($androidJar, $apiJar, $aidlJar, $sharedJar, $providerJar, $annotationJar) -join ';'
$sources = @(
    Get-ChildItem (Join-Path $main 'java') -Recurse -Filter *.java | ForEach-Object { $_.FullName }
)
$sources += @(Get-ChildItem (Join-Path $out 'gen') -Recurse -Filter *.java | ForEach-Object { $_.FullName })
$javacArgs = @('-encoding', 'UTF-8', '--release', '17', '-classpath', $classpath,
    '-d', (Join-Path $out 'classes')) + $sources
& (Join-Path $javaHome 'bin\javac.exe') @javacArgs
if ($LASTEXITCODE -ne 0) { throw 'javac failed' }

Write-Host '[5/8] d8'
$classFiles = @(Get-ChildItem (Join-Path $out 'classes') -Recurse -Filter *.class | ForEach-Object { $_.FullName })
$d8Args = @('--lib', $androidJar, '--min-api', '26', '--output', (Join-Path $out 'dex'))
if (Test-Path $annotationJar) { $d8Args += @('--classpath', $annotationJar) }
$d8Args += $classFiles
$d8Args += @($apiJar, $aidlJar, $sharedJar, $providerJar)
& (Join-Path $buildTools 'd8.bat') @d8Args
if ($LASTEXITCODE -ne 0) { throw 'd8 failed' }

Write-Host '[6/8] package dex into apk'
Add-Type -AssemblyName System.IO.Compression.FileSystem
Add-Type -AssemblyName System.IO.Compression
$unsigned = Join-Path $out 'unsigned.apk'
Copy-Item $baseApk $unsigned
$zip = [System.IO.Compression.ZipFile]::Open($unsigned, [System.IO.Compression.ZipArchiveMode]::Update)
try {
    [System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile(
        $zip,
        (Join-Path $out 'dex\classes.dex'),
        'classes.dex',
        [System.IO.Compression.CompressionLevel]::Optimal) | Out-Null
}
finally {
    $zip.Dispose()
}

Write-Host '[7/8] zipalign'
$aligned = Join-Path $out 'aligned.apk'
& (Join-Path $buildTools 'zipalign.exe') -f 4 $unsigned $aligned
if ($LASTEXITCODE -ne 0) { throw 'zipalign failed' }

Write-Host '[8/8] sign'
$keystore = Join-Path $project 'debug.keystore'
if (-not (Test-Path $keystore)) {
    & (Join-Path $javaHome 'bin\keytool.exe') -genkeypair -v `
        -keystore $keystore `
        -storepass android -alias androiddebugkey -keypass android `
        -keyalg RSA -keysize 2048 -validity 10000 `
        -dname 'CN=Android Debug,O=Android,C=US'
    if ($LASTEXITCODE -ne 0) { throw 'keytool failed' }
}

$signed = Join-Path $out 'Hz120Switch.apk'
& (Join-Path $buildTools 'apksigner.bat') sign `
    --ks $keystore --ks-pass pass:android --key-pass pass:android `
    --out $signed $aligned
if ($LASTEXITCODE -ne 0) { throw 'apksigner sign failed' }

& (Join-Path $buildTools 'apksigner.bat') verify $signed
if ($LASTEXITCODE -ne 0) { throw 'apksigner verify failed' }

$item = Get-Item $signed
Write-Host ''
Write-Host ("OK  {0}  ({1:N0} bytes)" -f $item.FullName, $item.Length) -ForegroundColor Green
