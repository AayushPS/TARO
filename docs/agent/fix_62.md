Cycle       : 62
Finding     : dc59ab7e
Type        : STYLE
Location    : src/main/java/org/Aayush/routing/execution/DefaultExecutionRuntimeBinder.java:[88]
Description : LineLength: Line is longer than 80 characters (found 98).

Root cause  : The incompatibility exception message was written as one long concatenation line, which exceeded the repo's 80-character Checkstyle limit.

Edit summary:
  File      : src/main/java/org/Aayush/routing/execution/DefaultExecutionRuntimeBinder.java
  Change    : Reflowed the `DIJKSTRA` incompatibility message across shorter concatenated string segments so the selected line clears Checkstyle without changing the thrown message text.

Scope check : Style only. The change affects one exception message expression and preserves the same message content, exception type, and control flow.

Targeted validation note:
  Command   : mvn checkstyle:check -q
  Result    : PASS for the selected finding; the original line-88 violation no longer appears, while unrelated Checkstyle backlog remains.
