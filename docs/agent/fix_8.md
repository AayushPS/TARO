Cycle       : 8
Finding     : ff524e36
Type        : STATIC
Location    : mvn verify
Description : State pool leak warning reported during verify: WARNING: 1 states leaked (extracted but not recycled). Replenishing pool.

Root cause  : The leak-recovery regression test intentionally exercised a missing recycle path and let the expected diagnostic warning escape to process stderr, so `mvn verify` reported a warning even though the queue recovery behavior was functioning as designed.

Edit summary:
  File      : src/test/java/org/Aayush/routing/search/SearchInfrastructureTest.java
  Change    : Captured the expected leak-recovery warning inside the focused regression test and asserted on it locally so the test still validates the diagnostic without polluting global verify output.

Scope check : Test-only. The change narrows warning handling to the intentional regression test path and does not alter queue runtime behavior.

Targeted validation note:
  Command   : mvn -Dtest=SearchInfrastructureTest test -q
  Result    : PASS
