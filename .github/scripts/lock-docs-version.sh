#!/usr/bin/env bash
# Lock the docs of a release into their bxSites version folder and serve it at the site root.
#
# Usage: lock-docs-version.sh <released version> <release docs root> <target docs root>
#   released version  : the version just released, ex: 1.2.3
#   release docs root : a checkout of the released commit (its docs/ is what shipped)
#   target docs root  : the checkout that keeps the docs (development)
#
# The docs line is MAJOR.MINOR.x: 1.2.3 locks into docs/versions/1.2.x. A patch release refreshes
# that folder, a minor or major release creates a new one. The target's bxsites.yaml then points
# versions.default at it, so the site root serves the released docs and docs/ is served at /next/.
set -euo pipefail

VERSION="${1:?released version required}"
RELEASE_ROOT="${2:?release docs root required}"
TARGET_ROOT="${3:?target docs root required}"
DOCS_VERSION="$( echo "$VERSION" | cut -d. -f1,2 ).x"

# Snapshot the released docs/ with bxSites, which leaves out assets/, versions/, i18n/ and blog/
rm -rf "$RELEASE_ROOT/docs/versions/$DOCS_VERSION"
( cd "$RELEASE_ROOT" && bxSites version:new --name="$DOCS_VERSION" )

rm -rf "$TARGET_ROOT/docs/versions/$DOCS_VERSION"
mkdir -p "$TARGET_ROOT/docs/versions"
cp -R "$RELEASE_ROOT/docs/versions/$DOCS_VERSION" "$TARGET_ROOT/docs/versions/$DOCS_VERSION"

# Replace the versions: block of bxsites.yaml (or add it) with the new default
python3 - "$TARGET_ROOT/bxsites.yaml" "$DOCS_VERSION" <<'PY'
import re, sys
path, version = sys.argv[1], sys.argv[2]
text = open( path ).read()
text = re.sub( r"(?m)^versions:\n(?:[ \t]+.*\n?|[ \t]*\n)*", "", text ).rstrip( "\n" )
open( path, "w" ).write( f'{text}\nversions:\n    default: "{version}"\n' )
PY

echo "Locked the docs of v$VERSION into docs/versions/$DOCS_VERSION, now served at the site root"
