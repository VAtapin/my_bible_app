[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$KeyDirectory,
    [ValidateRange(1, 2100000000)][int]$VersionCode = 1,
    [ValidatePattern('^[0-9]+\.[0-9]+\.[0-9]+(?:[-.][A-Za-z0-9.-]+)?$')][string]$VersionName = '0.1.0',
    [string]$JavaDirectory = 'C:\Program Files\Android\Android Studio\jbr'
)
$ErrorActionPreference = 'Stop'
$mobile = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$repository = [IO.Path]::GetFullPath((Join-Path $mobile '..'))
$directory = [IO.Path]::GetFullPath($KeyDirectory)
if (-not [IO.Path]::IsPathRooted($KeyDirectory) -or $directory.StartsWith($repository + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase) -or $directory -eq $repository) {
    throw 'Signing material must stay outside the repository.'
}
$key = Join-Path $directory 'upload.p12'
$passwordFile = Join-Path $directory 'upload-password.protected'
if (-not (Test-Path -LiteralPath $key -PathType Leaf) -or -not (Test-Path -LiteralPath $passwordFile -PathType Leaf)) {
    throw 'Upload key or its local protected password is missing. Create a new key only for a first publication; never replace an existing app key.'
}
$secure = ConvertTo-SecureString (Get-Content -LiteralPath $passwordFile -Raw)
$pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
$names = @('JAVA_HOME', 'BIBLE_RELEASE_STORE_FILE', 'BIBLE_RELEASE_STORE_PASSWORD', 'BIBLE_RELEASE_KEY_ALIAS', 'BIBLE_RELEASE_KEY_PASSWORD')
$previous = @{}
foreach ($name in $names) { $previous[$name] = [Environment]::GetEnvironmentVariable($name, 'Process') }
try {
    $env:JAVA_HOME = $JavaDirectory
    $env:BIBLE_RELEASE_STORE_FILE = $key
    $env:BIBLE_RELEASE_STORE_PASSWORD = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer)
    $env:BIBLE_RELEASE_KEY_ALIAS = 'upload'
    $env:BIBLE_RELEASE_KEY_PASSWORD = $env:BIBLE_RELEASE_STORE_PASSWORD
    Push-Location $mobile
    try {
        # Signing secrets must not be serialized into a Gradle configuration cache.
        & .\gradlew.bat :sharedLogic:testAndroidHostTest :androidApp:lintRelease :androidApp:bundleRelease :androidApp:assembleRelease --no-configuration-cache --console=plain "-PbibleVersionCode=$VersionCode" "-PbibleVersionName=$VersionName"
        if ($LASTEXITCODE -ne 0) { throw 'Release build failed.' }
        $aab = Join-Path $mobile 'androidApp\build\outputs\bundle\release\androidApp-release.aab'
        $apk = Join-Path $mobile 'androidApp\build\outputs\apk\release\androidApp-release.apk'
        $signatureReport = & (Join-Path $JavaDirectory 'bin\jarsigner.exe') -verify $aab 2>&1
        if ($LASTEXITCODE -ne 0) { throw 'AAB signature verification failed.' }
        Add-Type -AssemblyName System.IO.Compression.FileSystem
        $archive = [IO.Compression.ZipFile]::OpenRead($aab)
        try {
            if (-not ($archive.Entries | Where-Object { $_.FullName -match '^META-INF/[^/]+\.(RSA|DSA|EC)$' })) { throw 'AAB is unsigned.' }
        } finally { $archive.Dispose() }
        & (Join-Path $PSScriptRoot 'Test-ReleaseApk.ps1') -ApkPath $apk -JavaDirectory $JavaDirectory
        Write-Output 'PASS: AAB JAR signature. A self-signed Android upload certificate is expected.'
        Write-Output "Signed AAB: $aab"
        Write-Output "Signed APK for device checks: $apk"
        Get-FileHash -LiteralPath $aab, $apk -Algorithm SHA256 | Select-Object Path, Hash
        Write-Output 'This is the upload certificate, not necessarily the Play App Signing certificate. Do not use it for production App Links automatically.'
    } finally { Pop-Location }
} finally {
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer)
    $secure.Dispose()
    foreach ($name in $names) { [Environment]::SetEnvironmentVariable($name, $previous[$name], 'Process') }
}
