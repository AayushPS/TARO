Cycle       : 60
Finding     : b290d82b
Type        : STYLE
Location    : src/main/java/org/Aayush/routing/execution/DefaultExecutionRuntimeBinder.java:[71]
Description : LineLength: Line is longer than 80 characters (found 94).

Root cause  : The missing-startup-config exception message was left as one long string literal, so the constructor argument line exceeded the repo's 80-character Checkstyle limit.

Edit summary:
  File      : src/main/java/org/Aayush/routing/execution/DefaultExecutionRuntimeBinder.java
  Change    : Split the long required-config message into two concatenated string literals so the selected line fits the style rule while preserving the exact thrown message.

Scope check : Style only. The change affects one exception message expression and preserves the same message text, exception type, and control flow.

Targeted validation note:
  Command   : mvn checkstyle:check -q
  Result    : PASS for the selected finding; the original line-71 violation no longer appears, while unrelated Checkstyle backlog remains.
