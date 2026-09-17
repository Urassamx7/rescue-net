#!/bin/sh
set -e
root=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$root"
if [ ! -f "$root/mvnw" ]; then
  echo "mvnw nao encontrado na raiz do projecto." >&2
  exit 1
fi
exec "$root/mvnw" "$@"
