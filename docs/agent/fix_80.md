Cycle       : 80
Finding     : af51655f
Type        : STYLE
Location    : src/main/java/org/Aayush/routing/execution/ExecutionProfileRegistry.java:[49]
Description : LineLength: Line is longer than 80 characters (found 103).

Root cause  : The duplicate-profile exception message remained on a single line after the earlier registry formatting fixes, so that constructor call still exceeded the repository's 80-character Checkstyle limit.

Edit summary:
  File      : src/main/java/org/Aayush/routing/execution/ExecutionProfileRegistry.java
  Change    : Wrapped the duplicate-profile IllegalArgumentException construction onto two lines to satisfy the line-length rule.

Scope check : Confirmed this is a formatting-only change within the selected finding boundary and does not alter runtime behavior.
