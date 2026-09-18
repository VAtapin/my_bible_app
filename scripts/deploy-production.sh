#!/usr/bin/env bash

set -Eeuo pipefail

readonly app_dir="/var/www/vhosts/bible-desktop.com/my_app"
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

echo "My Bible App build is ready in $app_dir/dist"
