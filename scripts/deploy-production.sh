#!/usr/bin/env bash

set -Eeuo pipefail

readonly app_dir="/var/www/vhosts/biblia-app.ru/httpdocs"
readonly node_bin="/opt/plesk/node/22/bin"
readonly azbuka_public_dir="/var/www/vhosts/bible-desktop.com/my_app/azbuka-web/dist"

cd "$app_dir"
export PATH="$node_bin:$PATH"

if [[ "$(node -p 'process.versions.node.split(".")[0]')" != "22" ]]; then
    echo "Expected Plesk Node.js 22." >&2
    exit 1
fi

if [[ ! -d "$azbuka_public_dir" || ! -w "$azbuka_public_dir" ]]; then
    echo "Azbuka public directory must exist and be writable: $azbuka_public_dir" >&2
    exit 1
fi

git pull --ff-only
npm ci
npm run build

test -s dist/index.html
test -s dist/sw.js
test -s azbuka-web/dist/index.html
test -s azbuka-web/dist/sw.js
for icon in bookmarks calendar library prayers setup; do
    test -s "dist/app-icons/${icon}.png"
done

cp -a azbuka-web/dist/. "$azbuka_public_dir/"
cmp -s azbuka-web/dist/index.html "$azbuka_public_dir/index.html"
cmp -s azbuka-web/dist/sw.js "$azbuka_public_dir/sw.js"

echo "Bible App build is ready in $app_dir/dist"
echo "Azbuka public files updated in $azbuka_public_dir"
