#!/bin/sh
set -e
root=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
echo "=== RescueNet: testes e2e (RMI, selecao, failover) ==="
exec "$root/_mvn.sh" verify -DskipUnitTests "$@"
