#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

echo "Repo: ${ROOT_DIR}"

if command -v python3 >/dev/null 2>&1; then
  echo "python3: $(python3 --version 2>&1)"
else
  echo "python3: missing"
fi

if command -v java >/dev/null 2>&1; then
  echo "java: $(java -version 2>&1 | head -n 1)"
else
  echo "java: missing"
fi

if command -v mvn >/dev/null 2>&1; then
  echo "mvn: $(mvn -version 2>&1 | head -n 1)"
else
  echo "mvn: missing"
fi

if [[ -x "${ROOT_DIR}/.venv/bin/python" ]]; then
  echo ".venv: present"
else
  echo ".venv: missing"
fi

if PYTHONPATH="${ROOT_DIR}/src/main/python" python3 - <<'PY' >/dev/null 2>&1
from Utils.IDMapper import IDMapper
mapper = IDMapper()
mapper.get_or_create("env-check")
PY
then
  echo "python import path: OK (src/main/python)"
else
  echo "python import path: FAIL (src/main/python)"
fi
