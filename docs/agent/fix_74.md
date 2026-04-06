Cycle       : 74
Finding     : 96239d21
Type        : STYLE
Location    : src/main/java/org/Aayush/routing/execution/ExecutionProfileRegistry.java:[28,62]
Description : JavadocMethod: Expected @param tag for 'profileSpecs'.

Root cause  : The constructor Javadoc was wrapped to satisfy line-length and parameter-finality rules, but the required parameter documentation was never added.

Edit summary:
  File      : src/main/java/org/Aayush/routing/execution/ExecutionProfileRegistry.java
  Change    : Added the missing @param documentation for the constructor's profileSpecs input.

Scope check : Confirmed this is a Javadoc-only change and does not alter behavior outside the selected finding boundary.
