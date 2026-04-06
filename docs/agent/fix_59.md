Cycle       : 59
Finding     : e27579e2
Type        : STYLE
Location    : src/main/java/org/Aayush/routing/execution/DefaultExecutionRuntimeBinder.java:[53]
Description : LineLength: Line is longer than 80 characters (found 98).

Root cause  : The named-profile registry lookup kept the variable declaration and ternary condition on one line, which pushed the statement past the repo's 80-character Checkstyle limit.

Edit summary:
  File      : src/main/java/org/Aayush/routing/execution/DefaultExecutionRuntimeBinder.java
  Change    : Split the `registry` assignment so the declaration and ternary condition occupy separate lines while preserving the same lookup logic.

Scope check : Style only. The change reformats one local assignment without changing any symbol, branch, or runtime behavior.

Targeted validation note:
  Command   : mvn checkstyle:check -q
  Result    : PASS for the selected finding; the original line-53 violation no longer appears, while unrelated Checkstyle backlog remains.
