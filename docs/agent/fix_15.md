Cycle       : 15
Finding     : eb69a3c2
Type        : STYLE
Location    : src/main/java/org/Aayush/routing/execution/ExecutionProfileSpec.java:[19,19]
Description : VisibilityModifier: Variable 'heuristicType' must be private and have accessor methods.

Root cause  : The immutable execution profile spec uses Lombok-generated accessors throughout, but the `heuristicType` field itself was still package-private, so Checkstyle flagged the declaration even though the accessor contract already existed.

Edit summary:
  File      : src/main/java/org/Aayush/routing/execution/ExecutionProfileSpec.java
  Change    : Made `heuristicType` private so the field declaration matches the class's immutable Lombok style and satisfies the selected VisibilityModifier rule.

Scope check : Style only. The change narrows field visibility without changing generated accessors or runtime behavior.

Targeted validation note:
  Command   : mvn checkstyle:check -q
  Result    : PASS for the selected finding; the original `heuristicType` VisibilityModifier line no longer appears, while unrelated Checkstyle backlog remains.
