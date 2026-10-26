$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$tempDirectory = Join-Path $root 'build/tmp/bun'
[IO.Directory]::CreateDirectory($tempDirectory) | Out-Null
$env:TEMP = $tempDirectory
$env:TMP = $tempDirectory
$env:TMPDIR = $tempDirectory
$env:BUN_INSTALL_CACHE_DIR = Join-Path $root 'build/tools/bun/install-cache'
[IO.Directory]::CreateDirectory($env:BUN_INSTALL_CACHE_DIR) | Out-Null
$bun = Join-Path $root 'build/tools/bun/bun-windows-x64-baseline/bun.exe'
Push-Location $PSScriptRoot
try {
    & $bun install --frozen-lockfile --linker=hoisted
    if ($LASTEXITCODE -ne 0) { throw 'Voyager dependency installation failed' }
    & $bun build collectblock/src/index.ts --target=node --format=esm --packages=external --outfile=collectblock/index.mjs
    if ($LASTEXITCODE -ne 0) { throw 'Voyager collectblock compilation failed' }
    $output = Join-Path $root 'build/agent/voyager-runtime.zip'
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $temp = $output + '.tmp'
    if (Test-Path -LiteralPath $temp) { Remove-Item -LiteralPath $temp }
    $zip = [IO.Compression.ZipFile]::Open($temp, 'Create')
    try {
        Get-ChildItem -LiteralPath $PSScriptRoot -Recurse -File | Where-Object { $_.FullName -notmatch '[\\/]node_modules[\\/]\.bin[\\/]' } | ForEach-Object {
            $name = $_.FullName.Substring($PSScriptRoot.Length + 1).Replace('\', '/')
            [IO.Compression.ZipFileExtensions]::CreateEntryFromFile($zip, $_.FullName, $name, 'Optimal') | Out-Null
        }
    } finally { $zip.Dispose() }
    Move-Item -LiteralPath $temp -Destination $output -Force
    $stream = [IO.File]::OpenRead($output)
    $sha = [Security.Cryptography.SHA256]::Create()
    try { [IO.File]::WriteAllText($output + '.sha256', ([BitConverter]::ToString($sha.ComputeHash($stream))).Replace('-', '').ToLowerInvariant()) }
    finally { $stream.Dispose(); $sha.Dispose() }
} finally { Pop-Location }
