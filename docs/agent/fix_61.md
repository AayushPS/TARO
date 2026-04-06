Cycle       : 61
Finding     : d0ca0a3f
Type        : STYLE
Location    : src/main/java/org/Aayush/routing/execution/DefaultExecutionRuntimeBinder.java:[84]
Description : LineLength: Line is longer than 80 characters (found 92).

Root cause  : The `DIJKSTRA` compatibility guard combined two comparisons on one line, which pushed the conditional past the repo's 80-character Checkstyle limit.

Edit summary:
  File      : src/main/java/org/Aayush/routing/execution/DefaultExecutionRuntimeBinder.java
  Change    : Wrapped the long compatibility `if` condition across two lines without changing the boolean expression or the guarded exception path.

Scope check : Style only. The change reformats one condition and preserves the same symbols, control flow, and runtime behavior.

Targeted validation note:
  Command   : mvn checkstyle:check -q
  Result    : PASS for the selected finding; the original line-84 violation no longer appears, while unrelated Checkstyle backlog remains.
