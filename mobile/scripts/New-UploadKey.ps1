[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$KeyDirectory,
    [string]$JavaDirectory = 'C:\Program Files\Android\Android Studio\jbr'
)
$ErrorActionPreference = 'Stop'
$repository = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..'))
$directory = [IO.Path]::GetFullPath($KeyDirectory)
if (-not [IO.Path]::IsPathRooted($KeyDirectory) -or $directory.StartsWith($repository + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase) -or $directory -eq $repository) {
    throw 'Choose an absolute key directory outside the repository.'
}
$keytool = Join-Path $JavaDirectory 'bin\keytool.exe'
if (-not (Test-Path -LiteralPath $keytool -PathType Leaf)) { throw 'JDK keytool was not found.' }
$key = Join-Path $directory 'upload.p12'
$passwordFile = Join-Path $directory 'upload-password.protected'
if ((Test-Path -LiteralPath $key) -or (Test-Path -LiteralPath $passwordFile)) { throw 'Existing signing material will not be overwritten.' }
New-Item -ItemType Directory -Path $directory -Force | Out-Null
$bytes = New-Object byte[] 32
$random = [Security.Cryptography.RandomNumberGenerator]::Create()
try { $random.GetBytes($bytes) } finally { $random.Dispose() }
$password = [Convert]::ToBase64String($bytes)
$secure = ConvertTo-SecureString -String $password -AsPlainText -Force
# DPAPI binds this encrypted password to the current Windows user; never print the plaintext.
$protected = ConvertFrom-SecureString -SecureString $secure
[IO.File]::WriteAllText($passwordFile, $protected, [Text.UTF8Encoding]::new($false))
$previousStore = $env:BIBLE_RELEASE_STORE_PASSWORD
$previousKey = $env:BIBLE_RELEASE_KEY_PASSWORD
try {
    $env:BIBLE_RELEASE_STORE_PASSWORD = $password
    $env:BIBLE_RELEASE_KEY_PASSWORD = $password
    & $keytool -genkeypair -keystore $key -storetype PKCS12 -alias upload -keyalg RSA -keysize 3072 -validity 10000 -dname 'CN=Bible Desktop Upload' -storepass:env BIBLE_RELEASE_STORE_PASSWORD -keypass:env BIBLE_RELEASE_KEY_PASSWORD
    if ($LASTEXITCODE -ne 0) { throw 'Upload key generation failed; inspect the named key directory before retrying.' }
    & $keytool -list -v -keystore $key -alias upload -storepass:env BIBLE_RELEASE_STORE_PASSWORD
    if ($LASTEXITCODE -ne 0) { throw 'Could not verify the generated upload certificate.' }
    Write-Output "Upload key created outside Git: $key"
    Write-Output 'Back up this key securely. Its encrypted password works only with this Windows user/profile; Play can reset an upload key if necessary.'
} finally {
    $env:BIBLE_RELEASE_STORE_PASSWORD = $previousStore
    $env:BIBLE_RELEASE_KEY_PASSWORD = $previousKey
    $password = $null
    $secure.Dispose()
    [Array]::Clear($bytes, 0, $bytes.Length)
}
