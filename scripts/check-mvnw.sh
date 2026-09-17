#!/bin/sh
set -e
root=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
echo "=== RescueNet: verificar Maven Wrapper ==="
exec "$root/_mvn.sh" -v
