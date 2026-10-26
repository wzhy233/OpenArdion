$ErrorActionPreference = 'Stop'
function Get-Sha256([string] $path) {
    $stream = [IO.File]::OpenRead($path)
    $sha = [Security.Cryptography.SHA256]::Create()
    try { return ([BitConverter]::ToString($sha.ComputeHash($stream))).Replace('-', '').ToLowerInvariant() }
    finally { $stream.Dispose(); $sha.Dispose() }
}
$project = Split-Path $PSScriptRoot -Parent
$tempDirectory = Join-Path $project 'build/tmp/bun'
[IO.Directory]::CreateDirectory($tempDirectory) | Out-Null
$env:TEMP = $tempDirectory
$env:TMP = $tempDirectory
$env:TMPDIR = $tempDirectory
$tools = Join-Path $project 'build/tools/bun'
$env:BUN_INSTALL_CACHE_DIR = Join-Path $tools 'install-cache'
[IO.Directory]::CreateDirectory($env:BUN_INSTALL_CACHE_DIR) | Out-Null
$bun = Join-Path $tools 'bun-windows-x64-baseline/bun.exe'
if (!(Test-Path -LiteralPath $bun)) {
    New-Item -ItemType Directory -Path $tools -Force | Out-Null
    $archive = Join-Path $tools 'bun.zip'
    Invoke-WebRequest -Uri 'https://github.com/oven-sh/bun/releases/download/bun-v1.4.4/bun-windows-x64-baseline.zip' -OutFile $archive
    if ((Get-Sha256 $archive) -ne '0f20ce6674d4f2dbcc8c5d59fdf99f7ba45f8c3b367e0f5781cbfa30a1c78d99') {
        throw 'Bun 1.4.4 archive checksum mismatch'
    }
    Expand-Archive -LiteralPath $archive -DestinationPath $tools -Force
}
$env:PATH = (Split-Path $bun -Parent) + ';' + $env:PATH
$env:OPENCODE_CHANNEL = 'ardion'
$env:OPENCODE_VERSION = '0.1.0-ardion'
Push-Location $PSScriptRoot
try {
    & $bun install --frozen-lockfile '--os=*' '--cpu=*' '--libc=*'
    if ($LASTEXITCODE -ne 0) { throw 'Agent dependency installation failed' }
    $output = Join-Path $project 'build/agent'
    foreach ($platform in @('windows-x64', 'linux-x64')) {
        & $bun run packages/cli/script/build.ts "--target=ardion-engine-$platform-baseline" --skip-install --skip-web-ui "--outdir=dist/$platform"
        if ($LASTEXITCODE -ne 0) { throw "Agent $platform compilation failed" }
        $platformOutput = Join-Path $output $platform
        New-Item -ItemType Directory -Path $platformOutput -Force | Out-Null
        $name = if ($platform -eq 'windows-x64') { 'ardion-engine.exe' } else { 'ardion-engine' }
        $engine = Join-Path $platformOutput $name
        Copy-Item -LiteralPath "packages/cli/dist/$platform/cli-$platform-baseline/bin/$name" -Destination $engine -Force
        [IO.File]::WriteAllText((Join-Path $platformOutput 'ardion-engine.sha256'), (Get-Sha256 $engine))
    }
    Copy-Item -LiteralPath 'LICENSE' -Destination (Join-Path $output 'OPENCODE-LICENSE') -Force
    Copy-Item -LiteralPath 'UPSTREAM.json' -Destination (Join-Path $output 'ardion-engine-source.json') -Force
} finally {
    Pop-Location
}
