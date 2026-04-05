Cycle       : 79
Finding     : b8cb7aa2
Type        : STYLE
Location    : src/main/java/org/Aayush/routing/execution/ExecutionProfileRegistry.java:[46]
Description : LineLength: Line is longer than 80 characters (found 96).

Root cause  : The duplicate-profile guard still kept the `putIfAbsent` assignment on one line after the prior constructor formatting fixes, leaving that statement over the repository's 80-character limit.

Edit summary:
  File      : src/main/java/org/Aayush/routing/execution/ExecutionProfileRegistry.java
  Change    : Wrapped the previous-profile assignment across two lines to satisfy the Checkstyle line-length rule.

Scope check : Confirmed this is a formatting-only change and does not alter behavior outside the selected finding boundary.
