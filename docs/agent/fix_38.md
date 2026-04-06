Cycle       : 38
Finding     : 39bc2a01
Type        : STYLE
Location    : src/main/java/org/Aayush/routing/execution/ExecutionRuntimeBinder.java:[43,34]
Description : VisibilityModifier: Variable 'heuristicProviderFactory' must be private and have accessor methods.

Root cause  : The `BindInput` value type still exposed package-visible fields even though Lombok `@Value` already exposes accessor methods, so Checkstyle advanced to `heuristicProviderFactory` once the earlier fields were cleaned up.

Edit summary:
  File      : src/main/java/org/Aayush/routing/execution/ExecutionRuntimeBinder.java
  Change    : Marked `heuristicProviderFactory` as `private` within the `BindInput` value type.

Scope check : Style only. The change tightens field visibility without altering generated accessors, behavior, or object shape.

Targeted validation note:
  Command   : mvn checkstyle:check -q
  Result    : PASS for the selected finding; the original `heuristicProviderFactory` visibility violation no longer appears, while unrelated Checkstyle backlog remains.
