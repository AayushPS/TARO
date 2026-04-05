Cycle       : 76
Finding     : e6f5743a
Type        : STYLE
Location    : src/main/java/org/Aayush/routing/execution/ExecutionProfileRegistry.java:[35]
Description : LineLength: Line is longer than 80 characters (found 102).

Root cause  : The constructor loop still had a single-line null-normalization assignment after earlier style-only cleanups, leaving that statement over the repository's 80-character limit.

Edit summary:
  File      : src/main/java/org/Aayush/routing/execution/ExecutionProfileRegistry.java
  Change    : Wrapped the nonNullSpec assignment across two lines to satisfy the Checkstyle line-length rule.

Scope check : Confirmed this is a formatting-only change and does not alter behavior outside the selected finding boundary.
