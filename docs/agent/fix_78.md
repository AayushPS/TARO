Cycle       : 78
Finding     : 565fb093
Type        : STYLE
Location    : src/main/java/org/Aayush/routing/execution/ExecutionProfileRegistry.java:[40]
Description : LineLength: Line is longer than 80 characters (found 84).

Root cause  : The builder initialization for the normalized execution profile remained on one line after the previous constructor cleanups, leaving that statement over the repository's 80-character limit.

Edit summary:
  File      : src/main/java/org/Aayush/routing/execution/ExecutionProfileRegistry.java
  Change    : Wrapped the normalizedSpec builder assignment across two lines to satisfy the Checkstyle line-length rule.

Scope check : Confirmed this is a formatting-only change and does not alter behavior outside the selected finding boundary.
