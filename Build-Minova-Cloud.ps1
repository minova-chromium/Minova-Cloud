param([switch]$Debug)
$ErrorActionPreference='Stop'
$projectPath=$PSScriptRoot
if(-not $env:JAVA_HOME){$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'}
if(-not (Test-Path -LiteralPath (Join-Path $env:JAVA_HOME 'bin\java.exe'))){throw 'Install Android Studio or set JAVA_HOME to a JDK 17 or newer.'}
if($Debug){& (Join-Path $projectPath 'gradlew.bat') -p $projectPath :app:assembleDebug --console=plain;if($LASTEXITCODE -ne 0){throw 'Debug build failed'};exit}
$signingPath=Join-Path $env:LOCALAPPDATA 'Minova Cloud\Android Signing'
New-Item -ItemType Directory -Force -Path $signingPath | Out-Null
$keyPath=Join-Path $signingPath 'minova-cloud-release.jks'
$passwordPath=Join-Path $signingPath 'password.dpapi'
if((Test-Path -LiteralPath $keyPath) -and -not (Test-Path -LiteralPath $passwordPath)){throw 'The release key exists but its encrypted password is missing. Restore the password backup; do not replace the key.'}
if(-not (Test-Path -LiteralPath $passwordPath)){
    $random=New-Object byte[] 36;$generator=[Security.Cryptography.RandomNumberGenerator]::Create();$generator.GetBytes($random);$generator.Dispose()
    $secure=ConvertTo-SecureString ([Convert]::ToBase64String($random)) -AsPlainText -Force
    ConvertFrom-SecureString $secure | Set-Content -LiteralPath $passwordPath
}
$secure=(Get-Content -LiteralPath $passwordPath -Raw).Trim() | ConvertTo-SecureString
$pointer=[Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
try {
    $env:MINOVA_KEYSTORE_PASSWORD=[Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer)
    $env:MINOVA_KEYSTORE=$keyPath
    if(-not (Test-Path -LiteralPath $keyPath)){
        & (Join-Path $env:JAVA_HOME 'bin\keytool.exe') -genkeypair -keystore $keyPath -storetype PKCS12 -storepass:env MINOVA_KEYSTORE_PASSWORD -keypass:env MINOVA_KEYSTORE_PASSWORD -alias minova-cloud -keyalg RSA -keysize 3072 -validity 10000 -dname 'CN=Minova Cloud, O=Minova, C=BE' -noprompt
        if($LASTEXITCODE -ne 0){throw 'Could not create the new app signing key.'}
    }
    & (Join-Path $projectPath 'gradlew.bat') -p $projectPath :app:assembleRelease --console=plain
    if($LASTEXITCODE -ne 0){throw 'Release build failed'}
    $releasePath=Join-Path $projectPath 'releases';New-Item -ItemType Directory -Force -Path $releasePath | Out-Null
    $apkPath=Join-Path $releasePath 'Minova-Cloud-1.0.0.apk'
    Copy-Item -LiteralPath (Join-Path $projectPath 'app\build\outputs\apk\release\app-release.apk') -Destination $apkPath -Force
    $checksum=(Get-FileHash -LiteralPath $apkPath -Algorithm SHA256).Hash.ToLowerInvariant()
    "$checksum  Minova-Cloud-1.0.0.apk" | Set-Content -LiteralPath (Join-Path $releasePath 'SHA256SUMS.txt')
    $certificate=& (Join-Path $env:JAVA_HOME 'bin\keytool.exe') -list -v -keystore $keyPath -storepass:env MINOVA_KEYSTORE_PASSWORD -alias minova-cloud
    $fingerprint=($certificate | Select-String '^\s*SHA256:\s*(.*)$').Matches.Groups[1].Value.Trim()
    if(-not $fingerprint){throw 'Could not read the app signing certificate fingerprint.'}
    @{version='1.0.0';versionCode=1;package='com.minova.cloud';sha256=$checksum;certificateSha256=$fingerprint;file='Minova-Cloud-1.0.0.apk'} | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $releasePath 'release.json')
    Write-Output "Release APK ready: $apkPath"
} finally {
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer)
    Remove-Item Env:MINOVA_KEYSTORE_PASSWORD -ErrorAction SilentlyContinue
    Remove-Item Env:MINOVA_KEYSTORE -ErrorAction SilentlyContinue
}
