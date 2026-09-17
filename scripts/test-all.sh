#!/bin/sh
set -e
root=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
echo "=== RescueNet: testes unitarios + e2e ==="
exec "$root/_mvn.sh" verify "$@"
