Cycle       : 2
Finding     : a479496f
Type        : TOOLING
Location    : pom.xml
Description : Maven SpotBugs plugin not configured; mvn checkstyle:check spotbugs:check cannot resolve prefix 'spotbugs'

Root cause  : The Maven build declared no SpotBugs plugin, so the required static-analysis scan command failed at plugin resolution before any analyzer could inspect project code.

Edit summary:
  File      : pom.xml
  Change    : Added the SpotBugs Maven plugin declaration so the mandated Java static-analysis command can resolve and execute the SpotBugs goal.

Scope check : Tooling only. The change affects Maven scan configuration, not runtime application behavior or test logic.

Pre-existing failures noted:
  Command   : .venv/bin/python -m mypy src/main/python --ignore-missing-imports -q
  Detail    : /home/aayushps/projects/TARO/.venv/bin/python: No module named mypy
  Command   : cd taro-frontend && npx eslint . --format compact
  Detail    : The compact formatter is no longer part of core ESLint. Install it manually with `npm install -D eslint-formatter-compact`
  Command   : node scripts/check-api-contract.js
  Detail    : Error: Cannot find module '/home/aayushps/projects/TARO/scripts/check-api-contract.js'
