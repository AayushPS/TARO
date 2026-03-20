#!/usr/bin/env bash
set -euo pipefail

if ! command -v mvn >/dev/null 2>&1; then
  echo "mvn not found. Install Maven 3.9+ or add a Maven wrapper before running Java tests." >&2
  exit 1
fi

mvn test "$@"
