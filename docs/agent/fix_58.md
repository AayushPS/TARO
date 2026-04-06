Cycle       : 58
Finding     : 1f0a43ff
Type        : STYLE
Location    : src/main/java/org/Aayush/routing/execution/DefaultExecutionRuntimeBinder.java:[45]
Description : LineLength: Line is longer than 80 characters (found 95).

Root cause  : The conflict-path exception message was kept as one long string literal, so the constructor argument line exceeded the repo's 80-character Checkstyle limit.

Edit summary:
  File      : src/main/java/org/Aayush/routing/execution/DefaultExecutionRuntimeBinder.java
  Change    : Split the long conflict message into two concatenated string literals so the selected line fits the style limit without changing the thrown error text.

Scope check : Style only. The change affects one exception message expression and preserves the same message content, exception type, and execution flow.

Targeted validation note:
  Command   : mvn checkstyle:check -q
  Result    : PASS for the selected finding; the original line-45 violation no longer appears, while unrelated Checkstyle backlog remains.
