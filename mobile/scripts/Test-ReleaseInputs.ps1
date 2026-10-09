[CmdletBinding()]
param([string]$JavaDirectory = 'C:\Program Files\Android\Android Studio\jbr')
$ErrorActionPreference = 'Stop'
$names = @('JAVA_HOME', 'BIBLE_RELEASE_STORE_FILE', 'BIBLE_RELEASE_STORE_PASSWORD', 'BIBLE_RELEASE_KEY_ALIAS', 'BIBLE_RELEASE_KEY_PASSWORD')
$previous = @{}
foreach ($name in $names) { $previous[$name] = [Environment]::GetEnvironmentVariable($name, 'Process') }
$mobile = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
function Assert-Rejected([string]$expected, [string[]]$extra = @()) {
    $output = & .\gradlew.bat :androidApp:verifyReleaseSigning --no-configuration-cache --console=plain @extra 2>&1
    $result = $LASTEXITCODE
    if ($result -eq 0 -or ($output -join "`n") -notlike "*$expected*") { throw "Release guard did not reject the expected invalid input: $expected" }
    Write-Output "PASS: $expected"
}
try {
    $env:JAVA_HOME = $JavaDirectory
    foreach ($name in $names | Where-Object { $_ -ne 'JAVA_HOME' }) { [Environment]::SetEnvironmentVariable($name, $null, 'Process') }
    Push-Location $mobile
    try {
        Assert-Rejected 'Release signing is not configured'
        Assert-Rejected 'bibleVersionCode must be a positive Play version code' @('-PbibleVersionCode=0')
        Assert-Rejected 'bibleVersionName must be a semantic version' @('-PbibleVersionName=not-a-version')
        $env:BIBLE_RELEASE_STORE_FILE = Join-Path $mobile 'local.properties'
        $env:BIBLE_RELEASE_STORE_PASSWORD = 'non-secret-test-input'
        $env:BIBLE_RELEASE_KEY_ALIAS = 'upload'
        $env:BIBLE_RELEASE_KEY_PASSWORD = 'non-secret-test-input'
        Assert-Rejected 'The release keystore must exist outside the repository'
    } finally { Pop-Location }
} finally {
    foreach ($name in $names) { [Environment]::SetEnvironmentVariable($name, $previous[$name], 'Process') }
}
