Cycle       : 6
Finding     : d4e35c90
Type        : TOOLING
Location    : taro-frontend/package.json
Description : ESLint compact formatter unavailable; frontend lint command cannot run with --format compact

Root cause  : The frontend workspace uses ESLint 9, where the `compact` formatter is no longer bundled with core ESLint, but the mandated scan command still requests that formatter.

Edit summary:
  File      : taro-frontend/package.json
  Change    : Added the missing ESLint compact formatter dev dependency via the workspace package manager so the mandated lint command can resolve the formatter.
  File      : taro-frontend/package-lock.json
  Change    : Updated the lockfile to record the formatter dependency in the frontend workspace.

Scope check : Tooling only. The change affects frontend lint command dependencies, not application runtime behavior.

Targeted validation note:
  Command   : cd taro-frontend && npx eslint . --format compact
  Result    : PASS

Pre-existing failures noted:
  Command   : node scripts/check-api-contract.js
  Detail    : Error: Cannot find module '/home/aayushps/projects/TARO/scripts/check-api-contract.js'
