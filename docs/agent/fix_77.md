Cycle       : 77
Finding     : 2ae2ddd3
Type        : STYLE
Location    : src/main/java/org/Aayush/routing/execution/ExecutionProfileRegistry.java:[37]
Description : LineLength: Line is longer than 80 characters (found 108).

Root cause  : The constructor still kept the profile id normalization call on one line after the previous cycle's null-spec wrapping, leaving the next longest statement over the 80-character limit.

Edit summary:
  File      : src/main/java/org/Aayush/routing/execution/ExecutionProfileRegistry.java
  Change    : Wrapped the profileId normalization call across multiple lines to satisfy the Checkstyle line-length rule.

Scope check : Confirmed this is a formatting-only change and does not alter behavior outside the selected finding boundary.
