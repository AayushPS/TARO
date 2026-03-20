#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
VENV_DIR="${ROOT_DIR}/.venv"
INSTALL_DEV=0

usage() {
  cat <<'EOF'
Usage: ./scripts/bootstrap_env.sh [--with-dev]

Creates a local virtual environment and links the TARO Python utilities
from src/main/python into that environment.

Options:
  --with-dev   Also install optional Python dev dependencies (for example pytest)
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --with-dev)
      INSTALL_DEV=1
      shift
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "Unknown option: $1" >&2
      usage >&2
      exit 2
      ;;
  esac
done

python3 -m venv "${VENV_DIR}"
source "${VENV_DIR}/bin/activate"

SITE_PACKAGES="$(python - <<'PY'
import site
paths = [path for path in site.getsitepackages() if path.endswith("site-packages")]
if not paths:
    raise SystemExit("site-packages directory not found")
print(paths[0])
PY
)"

printf '%s\n' "${ROOT_DIR}/src/main/python" > "${SITE_PACKAGES}/taro_src_python.pth"

if [[ "${INSTALL_DEV}" -eq 1 ]]; then
  python -m pip install pytest
fi

cat <<EOF
Python environment ready.

Activate it with:
  source .venv/bin/activate

Then run:
  ./scripts/run_python_tests.sh
EOF
