Cycle       : 3
Finding     : c1760d3a
Type        : TOOLING
Location    : pyproject.toml
Description : mypy is not installed in .venv; python type check cannot run

Root cause  : The repo’s Python dev dependency set and bootstrap script installed pytest but not mypy, so the mandated type-check command could not start inside the project virtual environment.

Edit summary:
  File      : pyproject.toml
  Change    : Added mypy to the Python dev dependency set.
  File      : scripts/bootstrap_env.sh
  Change    : Updated the dev bootstrap path to install mypy alongside pytest so newly created environments include the required type-check tool.

Scope check : Tooling only. The changes affect Python environment setup, not runtime application behavior or test logic.

Targeted validation note:
  Command   : .venv/bin/python -m mypy src/main/python --ignore-missing-imports -q
  Result    : The original "No module named mypy" failure disappeared. A new pre-existing tooling issue was exposed instead: the mandated command passes `-q`, which this mypy CLI rejects.

Pre-existing failures noted:
  Command   : .venv/bin/python -m mypy src/main/python --ignore-missing-imports -q
  Detail    : usage: mypy ... mypy: error: unrecognized arguments: -q
  Command   : cd taro-frontend && npx eslint . --format compact
  Detail    : The compact formatter is no longer part of core ESLint. Install it manually with `npm install -D eslint-formatter-compact`
  Command   : node scripts/check-api-contract.js
  Detail    : Error: Cannot find module '/home/aayushps/projects/TARO/scripts/check-api-contract.js'
