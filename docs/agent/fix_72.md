Cycle       : 72
Finding     : e4a56e3e
Type        : STYLE
Location    : src/main/java/org/Aayush/routing/execution/ExecutionProfileRegistry.java:[27]
Description : LineLength: Line is longer than 80 characters (found 94).

Root cause  : The constructor signature was kept on one line even after the type and wildcard generic made it exceed the repository's 80-character limit.

Edit summary:
  File      : src/main/java/org/Aayush/routing/execution/ExecutionProfileRegistry.java
  Change    : Wrapped the constructor signature across two lines to satisfy the line-length rule without changing behavior.

Scope check : Confirmed this is a formatting-only change and does not alter behavior outside the selected finding boundary.
