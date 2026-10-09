[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$ApkPath,
    [string]$SdkDirectory = 'C:\Users\atapi\AppData\Local\Android\Sdk',
    [string]$JavaDirectory = 'C:\Program Files\Android\Android Studio\jbr',
    [switch]$RequireRelroAlignment
)
$ErrorActionPreference = 'Stop'
$previousJava = $env:JAVA_HOME
$env:JAVA_HOME = $JavaDirectory
try {
    $tools = Join-Path $SdkDirectory 'build-tools\37.0.0'
    $analyzer = Join-Path $SdkDirectory 'cmdline-tools\latest\bin\apkanalyzer.bat'
    & (Join-Path $tools 'apksigner.bat') verify --print-certs $ApkPath
    if ($LASTEXITCODE -ne 0) { throw 'APK signature is invalid.' }
    & (Join-Path $tools 'zipalign.exe') -c -P 16 4 $ApkPath
    if ($LASTEXITCODE -ne 0) { throw 'APK ZIP alignment is not 16 KB compatible.' }
    $package = & $analyzer manifest application-id $ApkPath
    if ($LASTEXITCODE -ne 0 -or $package.Trim() -ne 'com.bibledesktop.myapp') { throw 'Wrong release package.' }
    $debuggable = & $analyzer manifest debuggable $ApkPath
    if ($LASTEXITCODE -ne 0 -or $debuggable.Trim() -ne 'false') { throw 'Release must not be debuggable.' }
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $archive = [IO.Compression.ZipFile]::OpenRead([IO.Path]::GetFullPath($ApkPath))
    try {
        $libraries = @($archive.Entries | Where-Object { $_.FullName -match '^lib/(arm64-v8a|x86_64)/[^/]+\.so$' })
        foreach ($entry in $libraries) {
            $stream = $entry.Open()
            $memory = [IO.MemoryStream]::new()
            try { $stream.CopyTo($memory); $bytes = $memory.ToArray() } finally { $stream.Dispose(); $memory.Dispose() }
            if ($bytes.Length -lt 64 -or $bytes[0] -ne 0x7f -or $bytes[1] -ne 0x45 -or $bytes[2] -ne 0x4c -or $bytes[3] -ne 0x46 -or $bytes[4] -ne 2 -or $bytes[5] -ne 1) { throw "Unsupported ELF in $($entry.FullName)" }
            $offset = [BitConverter]::ToUInt64($bytes, 32)
            $size = [BitConverter]::ToUInt16($bytes, 54)
            $count = [BitConverter]::ToUInt16($bytes, 56)
            $loads = 0
            for ($i = 0; $i -lt $count; $i++) {
                $header = [int]($offset + $i * $size)
                if ($size -lt 56 -or $header + 56 -gt $bytes.Length) { throw 'Invalid ELF program header.' }
                $type = [BitConverter]::ToUInt32($bytes, $header)
                if ($type -eq 1) {
                    $loads++
                    if ([BitConverter]::ToUInt64($bytes, $header + 48) -lt 16384) { throw "ELF LOAD alignment below 16 KB: $($entry.FullName)" }
                }
                if ($type -eq 0x6474e552) {
                    $end = [BitConverter]::ToUInt64($bytes, $header + 16) + [BitConverter]::ToUInt64($bytes, $header + 40)
                    if ($end % 16384 -ne 0) {
                        $message = "ELF RELRO end is not 16 KB aligned: $($entry.FullName). Needs linker/device verification; static LOAD/ZIP checks alone are not full compatibility certification."
                        if ($RequireRelroAlignment) { throw $message }
                        Write-Warning $message
                    }
                }
            }
            if ($loads -eq 0) { throw 'ELF has no load segments.' }
            Write-Output "PASS: ELF LOAD 16 KB alignment: $($entry.FullName)"
        }
        Write-Output "PASS: release package, non-debuggable, signature, ZIP alignment ($($libraries.Count) 64-bit native libraries)"
    } finally { $archive.Dispose() }
} finally { $env:JAVA_HOME = $previousJava }
