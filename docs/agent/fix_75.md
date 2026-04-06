Cycle       : 75
Finding     : bd4f2933
Type        : STYLE
Location    : src/main/java/org/Aayush/routing/execution/ExecutionProfileRegistry.java:[31]
Description : LineLength: Line is longer than 80 characters (found 85).

Root cause  : The constructor-local registry map initialization was kept on a single line even after earlier Javadoc wrapping shifted the next visible Checkstyle backlog item into this block.

Edit summary:
  File      : src/main/java/org/Aayush/routing/execution/ExecutionProfileRegistry.java
  Change    : Wrapped the profiles map assignment across two lines to satisfy the 80-character line-length rule.

Scope check : Confirmed this is a formatting-only change and does not alter behavior outside the selected finding boundary.
