#!/bin/sh
set -e
root=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
echo "=== RescueNet: testes unitarios ==="
exec "$root/_mvn.sh" test "$@"
