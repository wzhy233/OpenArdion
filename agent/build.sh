#!/bin/sh
set -eu
root=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
case "$(uname -sm)" in
    "Linux x86_64") ;;
    *) echo 'Ardion desktop requires Linux x86_64 (glibc).' >&2; exit 1 ;;
esac
command -v bun >/dev/null 2>&1 || { echo 'Install Bun 1.4.4 and add it to PATH.' >&2; exit 1; }
[ "$(bun --version)" = '1.4.4' ] || { echo 'Ardion requires Bun 1.4.4.' >&2; exit 1; }
mkdir -p "$root/build/tmp/bun" "$root/build/tools/bun/install-cache" "$root/build/agent"
export TMPDIR="$root/build/tmp/bun"
export BUN_INSTALL_CACHE_DIR="$root/build/tools/bun/install-cache"
export OPENCODE_CHANNEL=ardion OPENCODE_VERSION=0.1.0-ardion
cd "$root/agent"
bun install --frozen-lockfile --os='*' --cpu='*' --libc='*'
for platform in windows-x64 linux-x64; do
    bun run packages/cli/script/build.ts "--target=ardion-engine-$platform-baseline" --skip-install --skip-web-ui "--outdir=dist/$platform"
    output="$root/build/agent/$platform"
    mkdir -p "$output"
    case "$platform" in
        windows-x64) name=ardion-engine.exe ;;
        linux-x64) name=ardion-engine ;;
    esac
    cp "packages/cli/dist/$platform/cli-$platform-baseline/bin/$name" "$output/$name"
    chmod 755 "$output/$name"
    sha256sum "$output/$name" | cut -d ' ' -f 1 > "$output/ardion-engine.sha256"
done
cp LICENSE "$root/build/agent/OPENCODE-LICENSE"
cp UPSTREAM.json "$root/build/agent/ardion-engine-source.json"
