Cycle       : 20
Finding     : 0befeb0d
Type        : STYLE
Location    : src/main/java/org/Aayush/routing/execution/ExecutionProfileSpec.java:[40]
Description : LineLength: Line is longer than 80 characters (found 93).

Root cause  : The `aStar` factory method signature placed both parameters on one line, pushing the declaration past the configured 80-character limit and triggering Checkstyle's line-length rule.

Edit summary:
  File      : src/main/java/org/Aayush/routing/execution/ExecutionProfileSpec.java
  Change    : Wrapped the `aStar` factory method signature across multiple lines to satisfy the selected line-length rule while leaving the remaining parameter-style findings untouched.

Scope check : Style only. The change reformats one method signature without altering behavior, names, or control flow.

Targeted validation note:
  Command   : mvn checkstyle:check -q
  Result    : PASS for the selected finding; the original line-length violation no longer appears, while unrelated Checkstyle backlog remains.
