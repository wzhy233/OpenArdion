#!/bin/sh
set -eu
root=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
mkdir -p "$root/build/tmp/bun" "$root/build/tools/bun/install-cache" "$root/build/agent"
export TMPDIR="$root/build/tmp/bun"
export BUN_INSTALL_CACHE_DIR="$root/build/tools/bun/install-cache"
cd "$root/voyager"
bun install --frozen-lockfile --linker=hoisted
bun build collectblock/src/index.ts --target=node --format=esm --packages=external --outfile=collectblock/index.mjs
python3 - "$root" <<'PY'
import hashlib
from pathlib import Path
import sys
from zipfile import ZipFile, ZIP_DEFLATED

root = Path(sys.argv[1])
source = root / 'voyager'
output = root / 'build/agent/voyager-runtime.zip'
temp = output.with_suffix('.zip.tmp')
with ZipFile(temp, 'w', ZIP_DEFLATED) as archive:
    # Follow workspace package symlinks: the Java extractor installs regular files.
    import os
    for directory, directories, files in os.walk(source, followlinks=True):
        directories[:] = [name for name in directories if name not in ('.bin', '.git')]
        for name in sorted(files):
            file = Path(directory) / name
            archive.write(file, file.relative_to(source).as_posix())
temp.replace(output)
with output.open('rb') as stream:
    digest = hashlib.file_digest(stream, 'sha256').hexdigest()
Path(str(output) + '.sha256').write_text(digest, encoding='ascii')
PY
