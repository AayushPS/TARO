Cycle       : 66
Finding     : e4cb3917
Type        : STYLE
Location    : src/main/java/org/Aayush/routing/execution/DefaultExecutionRuntimeBinder.java:[107]
Description : LineLength: Line is longer than 80 characters (found 87).

Root cause  : The `costEngine` null-check argument in the heuristic-provider factory call was kept on one long line, which exceeded the repo's 80-character Checkstyle limit.

Edit summary:
  File      : src/main/java/org/Aayush/routing/execution/DefaultExecutionRuntimeBinder.java
  Change    : Wrapped the `costEngine` null-check argument across two lines without changing the factory call inputs or their order.

Scope check : Style only. The change reformats one argument expression and preserves the same symbols, call order, and runtime behavior.

Targeted validation note:
  Command   : mvn checkstyle:check -q
  Result    : PASS for the selected finding; the original line-107 violation no longer appears, while unrelated Checkstyle backlog remains.
