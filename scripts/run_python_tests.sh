#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

if [[ -x "${ROOT_DIR}/.venv/bin/python" ]]; then
  PYTHON_BIN="${ROOT_DIR}/.venv/bin/python"
else
  PYTHON_BIN="python3"
fi

PYTHONPATH_PREFIX="${ROOT_DIR}/src/main/python"

if PYTHONPATH="${PYTHONPATH_PREFIX}" "${PYTHON_BIN}" -m pytest --version >/dev/null 2>&1; then
  if [[ $# -gt 0 ]]; then
    PYTHONPATH="${PYTHONPATH_PREFIX}" "${PYTHON_BIN}" -m pytest -q "$@"
  else
    PYTHONPATH="${PYTHONPATH_PREFIX}" "${PYTHON_BIN}" -m pytest -q "${ROOT_DIR}/src/main/python/tests"
  fi
else
  echo "pytest not available; falling back to unittest discover." >&2
  if [[ $# -gt 0 ]]; then
    PYTHONPATH="${PYTHONPATH_PREFIX}" "${PYTHON_BIN}" -m unittest "$@"
  else
    PYTHONPATH="${PYTHONPATH_PREFIX}" \
      "${PYTHON_BIN}" -m unittest discover -s "${ROOT_DIR}/src/main/python/tests" -v
  fi
fi
