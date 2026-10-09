#!/usr/bin/env bash

set -Eeuo pipefail

publish_azbuka=false
if [[ $# -eq 1 && "$1" == "--with-azbuka" ]]; then
    publish_azbuka=true
elif [[ $# -ne 0 ]]; then
    echo "Usage: bash scripts/deploy-production.sh [--with-azbuka]" >&2
    exit 2
fi

readonly app_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
readonly node_bin="/opt/plesk/node/22/bin"

cd "$app_dir"
export PATH="$node_bin:$PATH"

if [[ "$(node -p 'process.versions.node.split(".")[0]')" != "22" ]]; then
    echo "Expected Plesk Node.js 22." >&2
    exit 1
fi

git pull --ff-only
npm ci
npm run build

test -s dist/index.html
test -s dist/sw.js
for icon in bookmarks calendar library prayers setup; do
    test -s "dist/app-icons/${icon}.png"
done

echo "Bible App build is ready in $app_dir/dist"
if $publish_azbuka; then
    # Only the Azbuka owner imports this public archive into their own directory.
    test -s azbuka-web/dist/index.html
    test -s azbuka-web/dist/sw.js
    tar -czf dist/azbuka-release.tar.gz -C azbuka-web/dist .
    test -s dist/azbuka-release.tar.gz
    echo "Azbuka archive is ready at https://biblia-app.ru/azbuka-release.tar.gz"
    echo "Import it separately as the Plesk user serving azbuka.bible-desktop.com (see README)."
fi
