#!/usr/bin/env bash
# Compares the shared test vectors with the Jellio-Plugin (web) copy.
#
# The web client's tests/vectors/*.json are the source; this app runs a
# copy from app/src/test/resources/vectors (VectorsTest). To change a rule,
# edit the file in Jellio-Plugin, bump its "version", run that repo's
# scripts/check-vectors.sh --sync (or this one with --sync, which copies the
# web files here), and make both clients pass. CI in each repo only sees its
# own copy, so run this with both checked out side by side.
#
# Usage: scripts/check-vectors.sh [--sync] [path to Jellio-Plugin]
# The plugin path defaults to $JELLIO_DIR, then ../Jellio-Plugin, then ../Jellio.
set -euo pipefail

sync=false
if [ "${1:-}" = "--sync" ]; then
  sync=true
  shift
fi

here="$(cd "$(dirname "$0")/.." && pwd)"
web="${1:-${JELLIO_DIR:-}}"
if [ -z "$web" ]; then
  for candidate in "$here/../Jellio-Plugin" "$here/../Jellio"; do
    if [ -d "$candidate/tests/vectors" ]; then
      web="$candidate"
      break
    fi
  done
fi
ours="$here/app/src/test/resources/vectors"
theirs="${web:-$here/../Jellio-Plugin}/tests/vectors"

if [ ! -d "$theirs" ]; then
  echo "No Jellio-Plugin vectors at $theirs (pass the plugin checkout as an argument or set JELLIO_DIR)." >&2
  exit 2
fi

if $sync; then
  mkdir -p "$ours"
  rm -f "$ours"/*.json
  cp "$theirs"/*.json "$ours"/
  echo "Copied $(ls "$theirs"/*.json | wc -l) vector files to $ours"
  exit 0
fi

version() { sed -n 's/^  "version": \([0-9]*\),$/\1/p' "$1"; }

status=0
for file in "$ours"/*.json "$theirs"/*.json; do
  name="$(basename "$file")"
  if [ ! -f "$ours/$name" ] || [ ! -f "$theirs/$name" ]; then
    echo "only on one side: $name" >&2
    status=1
    continue
  fi
  [ "$file" = "$ours/$name" ] || continue
  if [ "$(version "$ours/$name")" != "$(version "$theirs/$name")" ]; then
    echo "version differs: $name (tv $(version "$ours/$name"), web $(version "$theirs/$name"))" >&2
    status=1
  elif ! cmp -s "$ours/$name" "$theirs/$name"; then
    echo "content differs at the same version: $name" >&2
    status=1
  fi
done

if [ $status -eq 0 ]; then
  echo "Vectors match ($(ls "$ours"/*.json | wc -l) files)."
else
  echo "Vectors differ. Bump the version of each changed file in Jellio-Plugin and run scripts/check-vectors.sh --sync." >&2
fi
exit $status
