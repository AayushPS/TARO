Cycle       : 23
Finding     : 5b826389
Type        : STYLE
Location    : src/main/java/org/Aayush/routing/execution/ExecutionProfileSpec.java:[43,13]
Description : FinalParameters: Parameter heuristicType should be final.

Root cause  : The `aStar` factory method still declared `heuristicType` as a mutable parameter even though the method only forwards it into the builder, so Checkstyle flagged the signature as inconsistent with the repository's final-parameter rule.

Edit summary:
  File      : src/main/java/org/Aayush/routing/execution/ExecutionProfileSpec.java
  Change    : Marked the `aStar` factory method's `heuristicType` parameter as `final`.

Scope check : Style only. The change tightens one method signature without altering behavior, control flow, or runtime data.

Targeted validation note:
  Command   : mvn checkstyle:check -q
  Result    : PASS for the selected finding; the original `ExecutionProfileSpec` `heuristicType` FinalParameters violation no longer appears, while unrelated Checkstyle backlog remains.
