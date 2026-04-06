Cycle       : 14
Finding     : 63c97b8b
Type        : STYLE
Location    : src/main/java/org/Aayush/routing/execution/ExecutionProfileSpec.java:[18,5]
Description : JavadocVariable: Missing a Javadoc comment.

Root cause  : The execution profile spec now documents `profileId` and `algorithm`, but the `heuristicType` field declaration was still undocumented, so Checkstyle flagged the next uncovered field in scan order.

Edit summary:
  File      : src/main/java/org/Aayush/routing/execution/ExecutionProfileSpec.java
  Change    : Added a field-level Javadoc comment for `heuristicType` to satisfy the selected JavadocVariable rule.

Scope check : Style only. The change adds documentation to one field and does not alter Lombok generation or runtime behavior.

Targeted validation note:
  Command   : mvn checkstyle:check -q
  Result    : PASS for the selected finding; the original `heuristicType` JavadocVariable line no longer appears, while unrelated Checkstyle backlog remains.
