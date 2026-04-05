Cycle       : 1
Scan ID     : 20260404_221417

Active findings after dedup:
  MAJOR | STATIC | mvn verify | State pool leak warning reported during verify: WARNING: 1 states leaked (extracted but not recycled). Replenishing pool. | HASH:ff524e36
  ADVISORY | TEST | mvn verify | Validation performance summary reported 1 warning | HASH:758d4160
  CRITICAL | TOOLING | pom.xml | Maven SpotBugs plugin not configured; mvn checkstyle:check spotbugs:check cannot resolve prefix 'spotbugs' | HASH:a479496f
  CRITICAL | TOOLING | pyproject.toml | mypy is not installed in .venv; python type check cannot run | HASH:c1760d3a
  CRITICAL | TOOLING | scripts/check-import-boundaries.js | Required scan script missing | HASH:53a300bd
  CRITICAL | TOOLING | taro-frontend/package.json | ESLint compact formatter unavailable; frontend lint command cannot run with --format compact | HASH:d4e35c90
  CRITICAL | TOOLING | scripts/check-api-contract.js | Required scan script missing | HASH:5d277d17

Regressions detected:
  none

Cycle       : 78
Scan ID     : 20260405_174322

Active findings after dedup:
  ADVISORY | TEST | mvn verify | Validation performance summary reported 1 warning | HASH:86103a35
  MINOR | STYLE | src/main/java/org/Aayush/routing/execution/ExecutionProfileRegistry.java:[40] | LineLength: Line is longer than 80 characters (found 84). | HASH:565fb093
  MINOR | STYLE | docs/agent/.scan_cycle78_java_static.log | 10554 additional Checkstyle violations remain in Java static analysis output (see log for line-level details) | HASH:a390f9d0

Regressions detected:
  none

Cycle       : 77
Scan ID     : 20260405_173708

Active findings after dedup:
  ADVISORY | TEST | mvn verify | Validation performance summary reported 1 warning | HASH:86103a35
  MINOR | STYLE | src/main/java/org/Aayush/routing/execution/ExecutionProfileRegistry.java:[37] | LineLength: Line is longer than 80 characters (found 108). | HASH:2ae2ddd3
  MINOR | STYLE | docs/agent/.scan_cycle77_java_static.log | 10555 additional Checkstyle violations remain in Java static analysis output (see log for line-level details) | HASH:db4a66a6

Regressions detected:
  none

Cycle       : 75
Scan ID     : 20260405_165938

Active findings after dedup:
  ADVISORY | TEST | mvn verify | Validation performance summary reported 1 warning | HASH:86103a35
  MINOR | STYLE | src/main/java/org/Aayush/routing/execution/ExecutionProfileRegistry.java:[31] | LineLength: Line is longer than 80 characters (found 85). | HASH:bd4f2933
  MINOR | STYLE | docs/agent/.scan_cycle75_java_static.log | 10557 additional Checkstyle violations remain in Java static analysis output (see log for line-level details) | HASH:8c2c9f94

Regressions detected:
  none

Cycle       : 76
Scan ID     : 20260405_172859

Active findings after dedup:
  ADVISORY | TEST | mvn verify | Validation performance summary reported 1 warning | HASH:86103a35
  MINOR | STYLE | src/main/java/org/Aayush/routing/execution/ExecutionProfileRegistry.java:[35] | LineLength: Line is longer than 80 characters (found 102). | HASH:e6f5743a
  MINOR | STYLE | docs/agent/.scan_cycle76_java_static.log | 10556 additional Checkstyle violations remain in Java static analysis output (see log for line-level details) | HASH:4fff4694

Regressions detected:
  none

Cycle       : 74
Scan ID     : 20260405_152356

Active findings after dedup:
  ADVISORY | TEST | mvn verify | Validation performance summary reported 1 warning | HASH:86103a35
  MINOR | STYLE | src/main/java/org/Aayush/routing/execution/ExecutionProfileRegistry.java:[28,62] | JavadocMethod: Expected @param tag for 'profileSpecs'. | HASH:96239d21
  MINOR | STYLE | docs/agent/.scan_cycle74_java_static.log | 10558 additional Checkstyle violations remain in Java static analysis output (see log for line-level details) | HASH:c5cbc641

Regressions detected:
  none

Cycle       : 73
Scan ID     : 20260405_152040

Active findings after dedup:
  ADVISORY | TEST | mvn verify | Validation performance summary reported 1 warning | HASH:86103a35
  MINOR | STYLE | src/main/java/org/Aayush/routing/execution/ExecutionProfileRegistry.java:[28,13] | FinalParameters: Parameter profileSpecs should be final. | HASH:bcf6b5c1
  MINOR | STYLE | docs/agent/.scan_cycle73_java_static.log | 10559 additional Checkstyle violations remain in Java static analysis output (see log for line-level details) | HASH:24c21a92

Regressions detected:
  none

Cycle       : 72
Scan ID     : 20260405_151737

Active findings after dedup:
  ADVISORY | TEST | mvn verify | Validation performance summary reported 1 warning | HASH:86103a35
  MINOR | STYLE | src/main/java/org/Aayush/routing/execution/ExecutionProfileRegistry.java:[27] | LineLength: Line is longer than 80 characters (found 94). | HASH:e4a56e3e
  MINOR | STYLE | docs/agent/.scan_cycle72_java_static.log | 10560 additional Checkstyle violations remain in Java static analysis output (see log for line-level details) | HASH:6d16d81a

Regressions detected:
  none

Cycle       : 71
Scan ID     : 20260405_151407

Active findings after dedup:
  ADVISORY | TEST | mvn verify | Validation performance summary reported 1 warning | HASH:86103a35
  MINOR | STYLE | src/main/java/org/Aayush/routing/execution/ExecutionProfileRegistry.java:[24] | LineLength: Line is longer than 80 characters (found 86). | HASH:9aceecb1
  MINOR | STYLE | docs/agent/.scan_cycle71_java_static.log | 10561 additional Checkstyle violations remain in Java static analysis output (see log for line-level details) | HASH:f27d37e0

Regressions detected:
  none

Cycle       : 69
Scan ID     : 20260405_150513

Active findings after dedup:
  ADVISORY | TEST | mvn verify | Validation performance summary reported 1 warning | HASH:86103a35
  MINOR | STYLE | src/main/java/org/Aayush/routing/execution/DefaultExecutionRuntimeBinder.java:[134,47] | FinalParameters: Parameter id should be final. | HASH:e8f51408
  MINOR | STYLE | docs/agent/.scan_cycle69_java_static.log | 10564 additional Checkstyle violations remain in Java static analysis output (see log for line-level details) | HASH:8e6bb147

Regressions detected:
  none

Cycle       : 68
Scan ID     : 20260405_150150

Active findings after dedup:
  ADVISORY | TEST | mvn verify | Validation performance summary reported 1 warning | HASH:86103a35
  MINOR | STYLE | src/main/java/org/Aayush/routing/execution/DefaultExecutionRuntimeBinder.java:[123] | LineLength: Line is longer than 80 characters (found 90). | HASH:c1ae99b1
  MINOR | STYLE | docs/agent/.scan_cycle68_java_static.log | 10564 additional Checkstyle violations remain in Java static analysis output (see log for line-level details) | HASH:cbcaaabd

Regressions detected:
  none

Cycle       : 67
Scan ID     : 20260405_145826

Active findings after dedup:
  ADVISORY | TEST | mvn verify | Validation performance summary reported 1 warning | HASH:86103a35
  MINOR | STYLE | src/main/java/org/Aayush/routing/execution/DefaultExecutionRuntimeBinder.java:[114] | LineLength: Line is longer than 80 characters (found 103). | HASH:ae9f9e90
  MINOR | STYLE | docs/agent/.scan_cycle67_java_static.log | 10565 additional Checkstyle violations remain in Java static analysis output (see log for line-level details) | HASH:876bce38

Regressions detected:
  none

Cycle       : 66
Scan ID     : 20260405_145445

Active findings after dedup:
  ADVISORY | TEST | mvn verify | Validation performance summary reported 1 warning | HASH:86103a35
  MINOR | STYLE | src/main/java/org/Aayush/routing/execution/DefaultExecutionRuntimeBinder.java:[107] | LineLength: Line is longer than 80 characters (found 87). | HASH:e4cb3917
  MINOR | STYLE | docs/agent/.scan_cycle66_java_static.log | 10567 additional Checkstyle violations remain in Java static analysis output (see log for line-level details) | HASH:5592d7d7

Regressions detected:
  none

Cycle       : 65
Scan ID     : 20260405_134809

Active findings after dedup:
  ADVISORY | TEST | mvn verify | Validation performance summary reported 1 warning | HASH:86103a35
  MINOR | STYLE | src/main/java/org/Aayush/routing/execution/DefaultExecutionRuntimeBinder.java:[105] | LineLength: Line is longer than 80 characters (found 91). | HASH:368d9303
  MINOR | STYLE | docs/agent/.scan_cycle65_java_static.log | 10568 additional Checkstyle violations remain in Java static analysis output (see log for line-level details) | HASH:2b48eec0

Regressions detected:
  none

Cycle       : 64
Scan ID     : 20260405_134405

Active findings after dedup:
  ADVISORY | TEST | mvn verify | Validation performance summary reported 1 warning | HASH:86103a35
  MINOR | STYLE | src/main/java/org/Aayush/routing/execution/DefaultExecutionRuntimeBinder.java:[103] | LineLength: Line is longer than 80 characters (found 85). | HASH:435f836e
  MINOR | STYLE | docs/agent/.scan_cycle64_java_static.log | 10569 additional Checkstyle violations remain in Java static analysis output (see log for line-level details) | HASH:e79ccd54

Regressions detected:
  none

Cycle       : 63
Scan ID     : 20260405_134027

Active findings after dedup:
  ADVISORY | TEST | mvn verify | Validation performance summary reported 1 warning | HASH:86103a35
  MINOR | STYLE | src/main/java/org/Aayush/routing/execution/DefaultExecutionRuntimeBinder.java:[94] | LineLength: Line is longer than 80 characters (found 83). | HASH:b5b5695b
  MINOR | STYLE | docs/agent/.scan_cycle63_java_static.log | 10569 additional Checkstyle violations remain in Java static analysis output (see log for line-level details) | HASH:b93d256c

Regressions detected:
  none

Cycle       : 62
Scan ID     : 20260405_133612

Active findings after dedup:
  ADVISORY | TEST | mvn verify | Validation performance summary reported 1 warning | HASH:86103a35
  MINOR | STYLE | src/main/java/org/Aayush/routing/execution/DefaultExecutionRuntimeBinder.java:[88] | LineLength: Line is longer than 80 characters (found 98). | HASH:dc59ab7e
  MINOR | STYLE | docs/agent/.scan_cycle62_java_static.log | 10571 additional Checkstyle violations remain in Java static analysis output (see log for line-level details) | HASH:2e69d906

Regressions detected:
  none

Cycle       : 61
Scan ID     : 20260405_133228

Active findings after dedup:
  ADVISORY | TEST | mvn verify | Validation performance summary reported 1 warning | HASH:86103a35
  MINOR | STYLE | src/main/java/org/Aayush/routing/execution/DefaultExecutionRuntimeBinder.java:[84] | LineLength: Line is longer than 80 characters (found 92). | HASH:d0ca0a3f
  MINOR | STYLE | docs/agent/.scan_cycle61_java_static.log | 10571 additional Checkstyle violations remain in Java static analysis output (see log for line-level details) | HASH:64aae308

Regressions detected:
  none

Cycle       : 60
Scan ID     : 20260405_124725

Active findings after dedup:
  ADVISORY | TEST | mvn verify | Validation performance summary reported 1 warning | HASH:86103a35
  MINOR | STYLE | src/main/java/org/Aayush/routing/execution/DefaultExecutionRuntimeBinder.java:[71] | LineLength: Line is longer than 80 characters (found 94). | HASH:b290d82b
  MINOR | STYLE | docs/agent/.scan_cycle60_java_static.log | 10572 additional Checkstyle violations remain in Java static analysis output (see log for line-level details) | HASH:6579de6f

Regressions detected:
  none

Cycle       : 59
Scan ID     : 20260405_124333

Active findings after dedup:
  ADVISORY | TEST | mvn verify | Validation performance summary reported 1 warning | HASH:86103a35
  MINOR | STYLE | src/main/java/org/Aayush/routing/execution/DefaultExecutionRuntimeBinder.java:[53] | LineLength: Line is longer than 80 characters (found 98). | HASH:e27579e2
  MINOR | STYLE | docs/agent/.scan_cycle59_java_static.log | 10573 additional Checkstyle violations remain in Java static analysis output (see log for line-level details) | HASH:396080d7

Regressions detected:
  none

Cycle       : 58
Scan ID     : 20260405_123903

Active findings after dedup:
  ADVISORY | TEST | mvn verify | Validation performance summary reported 1 warning | HASH:86103a35
  MINOR | STYLE | src/main/java/org/Aayush/routing/execution/DefaultExecutionRuntimeBinder.java:[45] | LineLength: Line is longer than 80 characters (found 95). | HASH:1f0a43ff
  MINOR | STYLE | docs/agent/.scan_cycle58_java_static.log | 10574 additional Checkstyle violations remain in Java static analysis output (see log for line-level details) | HASH:c5a64dd3

Regressions detected:
  none

Cycle       : 57
Scan ID     : 20260405_123455

Active findings after dedup:
  ADVISORY | TEST | mvn verify | Validation performance summary reported 1 warning | HASH:86103a35
  MINOR | STYLE | src/main/java/org/Aayush/routing/execution/DefaultExecutionRuntimeBinder.java:[40] | LineLength: Line is longer than 80 characters (found 88). | HASH:cf9f4b46
  MINOR | STYLE | docs/agent/.scan_cycle57_java_static.log | 10575 additional Checkstyle violations remain in Java static analysis output (see log for line-level details) | HASH:f849b8b1

Regressions detected:
  none

Cycle       : 56
Scan ID     : 20260405_123139

Active findings after dedup:
  ADVISORY | TEST | mvn verify | Validation performance summary reported 1 warning | HASH:86103a35
  MINOR | STYLE | src/main/java/org/Aayush/routing/execution/DefaultExecutionRuntimeBinder.java:[38] | LineLength: Line is longer than 80 characters (found 86). | HASH:063c9515
  MINOR | STYLE | docs/agent/.scan_cycle56_java_static.log | 10576 additional Checkstyle violations remain in Java static analysis output (see log for line-level details) | HASH:5841903d

Regressions detected:
  none

Cycle       : 55
Scan ID     : 20260405_122805

Active findings after dedup:
  ADVISORY | TEST | mvn verify | Validation performance summary reported 1 warning | HASH:86103a35
  MINOR | STYLE | src/main/java/org/Aayush/routing/execution/DefaultExecutionRuntimeBinder.java:[29] | LineLength: Line is longer than 80 characters (found 88). | HASH:b3a10802
  MINOR | STYLE | docs/agent/.scan_cycle55_java_static.log | 10577 additional Checkstyle violations remain in Java static analysis output (see log for line-level details) | HASH:d3f5f5bd

Regressions detected:
  none

Cycle       : 54
Scan ID     : 20260405_122448

Active findings after dedup:
  ADVISORY | TEST | mvn verify | Validation performance summary reported 1 warning | HASH:86103a35
  MINOR | STYLE | src/main/java/org/Aayush/routing/execution/DefaultExecutionRuntimeBinder.java:[27,25] | FinalParameters: Parameter input should be final. | HASH:a8011992
  MINOR | STYLE | docs/agent/.scan_cycle54_java_static.log | 10578 additional Checkstyle violations remain in Java static analysis output (see log for line-level details) | HASH:cb80078d

Regressions detected:
  none

Cycle       : 53
Scan ID     : 20260405_122115

Active findings after dedup:
  ADVISORY | TEST | mvn verify | Validation performance summary reported 1 warning | HASH:86103a35
  MINOR | STYLE | src/main/java/org/Aayush/routing/execution/DefaultExecutionRuntimeBinder.java:[20,5] | JavadocVariable: Missing a Javadoc comment. | HASH:3810b9c1
  MINOR | STYLE | docs/agent/.scan_cycle53_java_static.log | 10579 additional Checkstyle violations remain in Java static analysis output (see log for line-level details) | HASH:79faf215

Regressions detected:
  none

Cycle       : 52
Scan ID     : 20260405_120310

Active findings after dedup:
  ADVISORY | TEST | mvn verify | Validation performance summary reported 1 warning | HASH:86103a35
  MINOR | STYLE | src/main/java/org/Aayush/routing/execution/DefaultExecutionRuntimeBinder.java:[18,5] | JavadocVariable: Missing a Javadoc comment. | HASH:1859ddd1
  MINOR | STYLE | docs/agent/.scan_cycle52_java_static.log | 10580 additional Checkstyle violations remain in Java static analysis output (see log for line-level details) | HASH:c1c17e21

Regressions detected:
  none

Cycle       : 40
Scan ID     : 20260405_020938

Active findings after dedup:
  ADVISORY | TEST | mvn verify | Validation performance summary reported 1 warning | HASH:86103a35
  MINOR | STYLE | src/main/java/org/Aayush/routing/execution/ExecutionRuntimeBinder.java:[53,41] | VisibilityModifier: Variable 'resolvedExecutionProfileContext' must be private and have accessor methods. | HASH:a630ac06
  MINOR | STYLE | docs/agent/.scan_cycle40_java_static.log | 10599 additional Checkstyle violations remain in Java static analysis output (see log for line-level details) | HASH:92b987f1

Regressions detected:
  none

Cycle       : 41
Scan ID     : 20260405_021446

Active findings after dedup:
  ADVISORY | TEST | mvn verify | Validation performance summary reported 1 warning | HASH:86103a35
  MINOR | STYLE | src/main/java/org/Aayush/routing/execution/ExecutionRuntimeBinder.java:[54,9] | JavadocVariable: Missing a Javadoc comment. | HASH:2a2617a5
  MINOR | STYLE | docs/agent/.scan_cycle41_java_static.log | 10598 additional Checkstyle violations remain in Java static analysis output (see log for line-level details) | HASH:36bfbbe3

Regressions detected:
  none

Cycle       : 39
Scan ID     : 20260405_020521

Active findings after dedup:
  ADVISORY | TEST | mvn verify | Validation performance summary reported 1 warning | HASH:86103a35
  MINOR | STYLE | src/main/java/org/Aayush/routing/execution/ExecutionRuntimeBinder.java:[52,9] | JavadocVariable: Missing a Javadoc comment. | HASH:f1814046
  MINOR | STYLE | docs/agent/.scan_cycle39_java_static.log | 10600 additional Checkstyle violations remain in Java static analysis output (see log for line-level details) | HASH:4f4093e7

Regressions detected:
  none

Cycle       : 38
Scan ID     : 20260405_020200

Active findings after dedup:
  ADVISORY | TEST | mvn verify | Validation performance summary reported 1 warning | HASH:86103a35
  MINOR | STYLE | src/main/java/org/Aayush/routing/execution/ExecutionRuntimeBinder.java:[43,34] | VisibilityModifier: Variable 'heuristicProviderFactory' must be private and have accessor methods. | HASH:39bc2a01
  MINOR | STYLE | docs/agent/.scan_cycle38_java_static.log | 10601 additional Checkstyle violations remain in Java static analysis output (see log for line-level details) | HASH:eb41bca2

Regressions detected:
  none

Cycle       : 37
Scan ID     : 20260405_015808

Active findings after dedup:
  ADVISORY | TEST | mvn verify | Validation performance summary reported 1 warning | HASH:86103a35
  MINOR | STYLE | src/main/java/org/Aayush/routing/execution/ExecutionRuntimeBinder.java:[42,9] | JavadocVariable: Missing a Javadoc comment. | HASH:90d6a744
  MINOR | STYLE | docs/agent/.scan_cycle37_java_static.log | 10602 additional Checkstyle violations remain in Java static analysis output (see log for line-level details) | HASH:f2517685

Regressions detected:
  none

Cycle       : 36
Scan ID     : 20260405_015502

Active findings after dedup:
  ADVISORY | TEST | mvn verify | Validation performance summary reported 1 warning | HASH:86103a35
  MINOR | STYLE | src/main/java/org/Aayush/routing/execution/ExecutionRuntimeBinder.java:[41,23] | VisibilityModifier: Variable 'landmarkStore' must be private and have accessor methods. | HASH:8ec85f62
  MINOR | STYLE | docs/agent/.scan_cycle36_java_static.log | 10603 additional Checkstyle violations remain in Java static analysis output (see log for line-level details) | HASH:938f5dde

Regressions detected:
  none

Cycle       : 35
Scan ID     : 20260405_015116

Active findings after dedup:
  ADVISORY | TEST | mvn verify | Validation performance summary reported 1 warning | HASH:86103a35
  MINOR | STYLE | src/main/java/org/Aayush/routing/execution/ExecutionRuntimeBinder.java:[40,9] | JavadocVariable: Missing a Javadoc comment. | HASH:94912b82
  MINOR | STYLE | docs/agent/.scan_cycle35_java_static.log | 10604 additional Checkstyle violations remain in Java static analysis output (see log for line-level details) | HASH:c1bb979e

Regressions detected:
  none

Cycle       : 34
Scan ID     : 20260405_014814

Active findings after dedup:
  ADVISORY | TEST | mvn verify | Validation performance summary reported 1 warning | HASH:86103a35
  MINOR | STYLE | src/main/java/org/Aayush/routing/execution/ExecutionRuntimeBinder.java:[39,20] | VisibilityModifier: Variable 'costEngine' must be private and have accessor methods. | HASH:a0c4261e
  MINOR | STYLE | docs/agent/.scan_cycle34_java_static.log | 10605 additional Checkstyle violations remain in Java static analysis output (see log for line-level details) | HASH:ca4c81ef

Regressions detected:
  none

Cycle       : 33
Scan ID     : 20260405_014452

Active findings after dedup:
  ADVISORY | TEST | mvn verify | Validation performance summary reported 1 warning | HASH:86103a35
  MINOR | STYLE | src/main/java/org/Aayush/routing/execution/ExecutionRuntimeBinder.java:[38,9] | JavadocVariable: Missing a Javadoc comment. | HASH:cce4e19a
  MINOR | STYLE | docs/agent/.scan_cycle33_java_static.log | 10606 additional Checkstyle violations remain in Java static analysis output (see log for line-level details) | HASH:db811e23

Regressions detected:
  none

Cycle       : 32
Scan ID     : 20260405_014143

Active findings after dedup:
  ADVISORY | TEST | mvn verify | Validation performance summary reported 1 warning | HASH:86103a35
  MINOR | STYLE | src/main/java/org/Aayush/routing/execution/ExecutionRuntimeBinder.java:[37,22] | VisibilityModifier: Variable 'profileStore' must be private and have accessor methods. | HASH:e40d2c5c
  MINOR | STYLE | docs/agent/.scan_cycle32_java_static.log | 10607 additional Checkstyle violations remain in Java static analysis output (see log for line-level details) | HASH:1b75163b

Regressions detected:
  none

Cycle       : 31
Scan ID     : 20260405_013821

Active findings after dedup:
  ADVISORY | TEST | mvn verify | Validation performance summary reported 1 warning | HASH:86103a35
  MINOR | STYLE | src/main/java/org/Aayush/routing/execution/ExecutionRuntimeBinder.java:[36,9] | JavadocVariable: Missing a Javadoc comment. | HASH:d62f08a8
  MINOR | STYLE | docs/agent/.scan_cycle31_java_static.log | 10608 additional Checkstyle violations remain in Java static analysis output (see log for line-level details) | HASH:aa1fcaf4

Regressions detected:
  none

Cycle       : 30
Scan ID     : 20260405_013451

Active findings after dedup:
  ADVISORY | TEST | mvn verify | Validation performance summary reported 1 warning | HASH:86103a35
  MINOR | STYLE | src/main/java/org/Aayush/routing/execution/ExecutionRuntimeBinder.java:[35,19] | VisibilityModifier: Variable 'edgeGraph' must be private and have accessor methods. | HASH:e9cc12e6
  MINOR | STYLE | docs/agent/.scan_cycle30_java_static.log | 10609 additional Checkstyle violations remain in Java static analysis output (see log for line-level details) | HASH:ac86e807

Regressions detected:
  none

Cycle       : 29
Scan ID     : 20260405_013133

Active findings after dedup:
  ADVISORY | TEST | mvn verify | Validation performance summary reported 1 warning | HASH:86103a35
  MINOR | STYLE | src/main/java/org/Aayush/routing/execution/ExecutionRuntimeBinder.java:[34,9] | JavadocVariable: Missing a Javadoc comment. | HASH:c549716c
  MINOR | STYLE | docs/agent/.scan_cycle29_java_static.log | 10610 additional Checkstyle violations remain in Java static analysis output (see log for line-level details) | HASH:4aa21832

Regressions detected:
  none

Cycle       : 28
Scan ID     : 20260405_012849

Active findings after dedup:
  ADVISORY | TEST | mvn verify | Validation performance summary reported 1 warning | HASH:86103a35
  MINOR | STYLE | src/main/java/org/Aayush/routing/execution/ExecutionRuntimeBinder.java:[33,34] | VisibilityModifier: Variable 'executionProfileRegistry' must be private and have accessor methods. | HASH:63f7e9cf
  MINOR | STYLE | docs/agent/.scan_cycle28_java_static.log | 10611 additional Checkstyle violations remain in Java static analysis output (see log for line-level details) | HASH:8da5d443

Regressions detected:
  none

Cycle       : 27
Scan ID     : 20260405_012542

Active findings after dedup:
  ADVISORY | TEST | mvn verify | Validation performance summary reported 1 warning | HASH:86103a35
  MINOR | STYLE | src/main/java/org/Aayush/routing/execution/ExecutionRuntimeBinder.java:[32,9] | JavadocVariable: Missing a Javadoc comment. | HASH:471ee8fd
  MINOR | STYLE | docs/agent/.scan_cycle27_java_static.log | 10612 additional Checkstyle violations remain in Java static analysis output (see log for line-level details) | HASH:39141334

Regressions detected:
  none

Cycle       : 26
Scan ID     : 20260405_012259

Active findings after dedup:
  ADVISORY | TEST | mvn verify | Validation performance summary reported 1 warning | HASH:86103a35
  MINOR | STYLE | src/main/java/org/Aayush/routing/execution/ExecutionRuntimeBinder.java:[31,32] | VisibilityModifier: Variable 'executionRuntimeConfig' must be private and have accessor methods. | HASH:1b5b7eab
  MINOR | STYLE | docs/agent/.scan_cycle26_java_static.log | 10613 additional Checkstyle violations remain in Java static analysis output (see log for line-level details) | HASH:a63ca15c

Regressions detected:
  none

Cycle       : 25
Scan ID     : 20260405_012001

Active findings after dedup:
  ADVISORY | TEST | mvn verify | Validation performance summary reported 1 warning | HASH:86103a35
  MINOR | STYLE | src/main/java/org/Aayush/routing/execution/ExecutionRuntimeBinder.java:[30,9] | JavadocVariable: Missing a Javadoc comment. | HASH:e8014832
  MINOR | STYLE | docs/agent/.scan_cycle25_java_static.log | 10614 additional Checkstyle violations remain in Java static analysis output (see log for line-level details) | HASH:cbddc08e

Regressions detected:
  none

Cycle       : 24
Scan ID     : 20260405_011716

Active findings after dedup:
  ADVISORY | TEST | mvn verify | Validation performance summary reported 1 warning | HASH:86103a35
  MINOR | STYLE | src/main/java/org/Aayush/routing/execution/ExecutionProfileSpec.java:[43,33] | JavadocMethod: Expected @param tag for 'heuristicType'. | HASH:51475b56
  MINOR | STYLE | docs/agent/.scan_cycle24_java_static.log | 10615 additional Checkstyle violations remain in Java static analysis output (see log for line-level details) | HASH:f7d3c680

Regressions detected:
  none

Cycle       : 23
Scan ID     : 20260405_011412

Active findings after dedup:
  ADVISORY | TEST | mvn verify | Validation performance summary reported 1 warning | HASH:86103a35
  MINOR | STYLE | src/main/java/org/Aayush/routing/execution/ExecutionProfileSpec.java:[43,13] | FinalParameters: Parameter heuristicType should be final. | HASH:5b826389
  MINOR | STYLE | docs/agent/.scan_cycle23_java_static.log | 10616 additional Checkstyle violations remain in Java static analysis output (see log for line-level details) | HASH:c4681247

Regressions detected:
  none

Cycle       : 22
Scan ID     : 20260405_011041

Active findings after dedup:
  ADVISORY | TEST | mvn verify | Validation performance summary reported 1 warning | HASH:86103a35
  MINOR | STYLE | src/main/java/org/Aayush/routing/execution/ExecutionProfileSpec.java:[41,26] | JavadocMethod: Expected @param tag for 'profileId'. | HASH:8e941227
  MINOR | STYLE | docs/agent/.scan_cycle22_java_static.log | 10617 additional Checkstyle violations remain in Java static analysis output (see log for line-level details) | HASH:77327969

Regressions detected:
  none

Cycle       : 8
Scan ID     : 20260405_002218

Active findings after dedup:
  MAJOR | STATIC | mvn verify | State pool leak warning reported during verify: WARNING: 1 states leaked (extracted but not recycled). Replenishing pool. | HASH:ff524e36
  ADVISORY | TEST | mvn verify | Validation performance summary reported 1 warning | HASH:758d4160
  MINOR | STYLE | src/main/java/org/Aayush/routing/execution/*, src/main/java/org/Aayush/routing/overlay/*, src/main/java/org/Aayush/api/*, and src/main/java/org/Aayush/app/* | Checkstyle violations detected (missing package-info, missing Javadocs, line-length, visibility, final-parameter, hidden-field, design, magic-number, and parameter-count rules) | HASH:4c2f737a

Regressions detected:
  none

Cycle       : 9
Scan ID     : 20260405_002744

Active findings after dedup:
  ADVISORY | TEST | mvn verify | Validation performance summary reported 1 warning | HASH:86103a35
  MINOR | STYLE | src/main/java/org/Aayush/routing/execution/ExecutionProfileSpec.java:[1] | JavadocPackage: Missing package-info.java file. | HASH:07fad5ad
  MINOR | STYLE | docs/agent/.scan_cycle9_java_static.log | 10623 additional Checkstyle violations remain in Java static analysis output (see log for line-level details) | HASH:b815d1f6

Regressions detected:
  none

Cycle       : 10
Scan ID     : 20260405_003138

Active findings after dedup:
  ADVISORY | TEST | mvn verify | Validation performance summary reported 1 warning | HASH:86103a35
  MINOR | STYLE | src/main/java/org/Aayush/routing/execution/ExecutionProfileSpec.java:[14,5] | JavadocVariable: Missing a Javadoc comment. | HASH:c0da90d7
  MINOR | STYLE | docs/agent/.scan_cycle10_java_static.log | 10622 additional Checkstyle violations remain in Java static analysis output (see log for line-level details) | HASH:b42eec2a

Regressions detected:
  none

Cycle       : 11
Scan ID     : 20260405_003459

Active findings after dedup:
  ADVISORY | TEST | mvn verify | Validation performance summary reported 1 warning | HASH:86103a35
  MINOR | STYLE | src/main/java/org/Aayush/routing/execution/ExecutionProfileSpec.java:[15,12] | VisibilityModifier: Variable 'profileId' must be private and have accessor methods. | HASH:8e6bb2ba
  MINOR | STYLE | docs/agent/.scan_cycle11_java_static.log | 10628 additional Checkstyle violations remain in Java static analysis output (see log for line-level details) | HASH:f7a3c3e8

Regressions detected:
  none

Cycle       : 12
Scan ID     : 20260405_003747

Active findings after dedup:
  ADVISORY | TEST | mvn verify | Validation performance summary reported 1 warning | HASH:86103a35
  MINOR | STYLE | src/main/java/org/Aayush/routing/execution/ExecutionProfileSpec.java:[16,5] | JavadocVariable: Missing a Javadoc comment. | HASH:3b9568d4
  MINOR | STYLE | docs/agent/.scan_cycle12_java_static.log | 10627 additional Checkstyle violations remain in Java static analysis output (see log for line-level details) | HASH:60d5388e

Regressions detected:
  none

Cycle       : 13
Scan ID     : 20260405_004032

Active findings after dedup:
  ADVISORY | TEST | mvn verify | Validation performance summary reported 1 warning | HASH:86103a35
  MINOR | STYLE | src/main/java/org/Aayush/routing/execution/ExecutionProfileSpec.java:[17,22] | VisibilityModifier: Variable 'algorithm' must be private and have accessor methods. | HASH:13fa0d93
  MINOR | STYLE | docs/agent/.scan_cycle13_java_static.log | 10626 additional Checkstyle violations remain in Java static analysis output (see log for line-level details) | HASH:ed7c2e47

Regressions detected:
  none

Cycle       : 14
Scan ID     : 20260405_004319

Active findings after dedup:
  ADVISORY | TEST | mvn verify | Validation performance summary reported 1 warning | HASH:86103a35
  MINOR | STYLE | src/main/java/org/Aayush/routing/execution/ExecutionProfileSpec.java:[18,5] | JavadocVariable: Missing a Javadoc comment. | HASH:63c97b8b
  MINOR | STYLE | docs/agent/.scan_cycle14_java_static.log | 10625 additional Checkstyle violations remain in Java static analysis output (see log for line-level details) | HASH:d53c1a00

Regressions detected:
  none

Cycle       : 15
Scan ID     : 20260405_004645

Active findings after dedup:
  ADVISORY | TEST | mvn verify | Validation performance summary reported 1 warning | HASH:86103a35
  MINOR | STYLE | src/main/java/org/Aayush/routing/execution/ExecutionProfileSpec.java:[19,19] | VisibilityModifier: Variable 'heuristicType' must be private and have accessor methods. | HASH:eb69a3c2
  MINOR | STYLE | docs/agent/.scan_cycle15_java_static.log | 10624 additional Checkstyle violations remain in Java static analysis output (see log for line-level details) | HASH:7ee5f133

Regressions detected:
  none

Cycle       : 16
Scan ID     : 20260405_004935

Active findings after dedup:
  ADVISORY | TEST | mvn verify | Validation performance summary reported 1 warning | HASH:86103a35
  MINOR | STYLE | src/main/java/org/Aayush/routing/execution/ExecutionProfileSpec.java:[24] | JavadocMethod: @return tag should be present and have description. | HASH:2ace6ecf
  MINOR | STYLE | docs/agent/.scan_cycle16_java_static.log | 10623 additional Checkstyle violations remain in Java static analysis output (see log for line-level details) | HASH:2453434c

Regressions detected:
  none

Cycle       : 17
Scan ID     : 20260405_005305

Active findings after dedup:
  ADVISORY | TEST | mvn verify | Validation performance summary reported 1 warning | HASH:86103a35
  MINOR | STYLE | src/main/java/org/Aayush/routing/execution/ExecutionProfileSpec.java:[26,49] | FinalParameters: Parameter profileId should be final. | HASH:e3eadd57
  MINOR | STYLE | docs/agent/.scan_cycle17_java_static.log | 10622 additional Checkstyle violations remain in Java static analysis output (see log for line-level details) | HASH:afae8239

Regressions detected:
  none

Cycle       : 18
Scan ID     : 20260405_005604

Active findings after dedup:
  ADVISORY | TEST | mvn verify | Validation performance summary reported 1 warning | HASH:86103a35
  MINOR | STYLE | src/main/java/org/Aayush/routing/execution/ExecutionProfileSpec.java:[26,62] | JavadocMethod: Expected @param tag for 'profileId'. | HASH:e53e9dd9
  MINOR | STYLE | docs/agent/.scan_cycle18_java_static.log | 10621 additional Checkstyle violations remain in Java static analysis output (see log for line-level details) | HASH:bafb249b

Regressions detected:
  none

Cycle       : 19
Scan ID     : 20260405_005930

Active findings after dedup:
  ADVISORY | TEST | mvn verify | Validation performance summary reported 1 warning | HASH:86103a35
  MINOR | STYLE | src/main/java/org/Aayush/routing/execution/ExecutionProfileSpec.java:[38] | JavadocMethod: @return tag should be present and have description. | HASH:85e56867
  MINOR | STYLE | docs/agent/.scan_cycle19_java_static.log | 10620 additional Checkstyle violations remain in Java static analysis output (see log for line-level details) | HASH:04a87614

Regressions detected:
  none

Cycle       : 20
Scan ID     : 20260405_010239

Active findings after dedup:
  ADVISORY | TEST | mvn verify | Validation performance summary reported 1 warning | HASH:86103a35
  MINOR | STYLE | src/main/java/org/Aayush/routing/execution/ExecutionProfileSpec.java:[40] | LineLength: Line is longer than 80 characters (found 93). | HASH:0befeb0d
  MINOR | STYLE | docs/agent/.scan_cycle20_java_static.log | 10619 additional Checkstyle violations remain in Java static analysis output (see log for line-level details) | HASH:c21290a8

Regressions detected:
  none

Cycle       : 21
Scan ID     : 20260405_010628

Active findings after dedup:
  ADVISORY | TEST | mvn verify | Validation performance summary reported 1 warning | HASH:86103a35
  MINOR | STYLE | src/main/java/org/Aayush/routing/execution/ExecutionProfileSpec.java:[41,13] | FinalParameters: Parameter profileId should be final. | HASH:63034150
  MINOR | STYLE | docs/agent/.scan_cycle21_java_static.log | 10618 additional Checkstyle violations remain in Java static analysis output (see log for line-level details) | HASH:e9969d4d

Regressions detected:
  none

Cycle       : 7
Scan ID     : 20260405_001823

Active findings after dedup:
  MAJOR | STATIC | mvn verify | State pool leak warning reported during verify: WARNING: 1 states leaked (extracted but not recycled). Replenishing pool. | HASH:ff524e36
  ADVISORY | TEST | mvn verify | Validation performance summary reported 1 warning | HASH:758d4160
  MINOR | STYLE | src/main/java/org/Aayush/routing/execution/* and src/main/java/org/Aayush/routing/overlay/LiveOverlay.java | Checkstyle violations detected (missing package-info, missing Javadocs, line-length, visibility, final-parameter, hidden-field, and magic-number rules) | HASH:4c2f737a
  MAJOR | STATIC | src/main/java/org/Aayush/api/*, src/main/java/org/Aayush/routing/*, src/main/java/org/Aayush/core/id/FastUtilIDMapper.java | SpotBugs findings detected (mutable representation exposure, floating-point equality, dead local stores, constructor-throw, compareTo/equals mismatch, public primitive attributes, unread field, and naming issues) | HASH:97549551
  CRITICAL | TOOLING | scripts/check-api-contract.js | Required scan script missing | HASH:5d277d17

Regressions detected:
  none

Cycle       : 6
Scan ID     : 20260405_001243

Active findings after dedup:
  MAJOR | STATIC | mvn verify | State pool leak warning reported during verify: WARNING: 1 states leaked (extracted but not recycled). Replenishing pool. | HASH:ff524e36
  ADVISORY | TEST | mvn verify | Validation performance summary reported 1 warning | HASH:758d4160
  MINOR | STYLE | src/main/java/org/Aayush/routing/execution/* and src/main/java/org/Aayush/routing/overlay/LiveOverlay.java | Checkstyle violations detected (missing package-info, missing Javadocs, line-length, visibility, final-parameter, hidden-field, and magic-number rules) | HASH:4c2f737a
  MAJOR | STATIC | src/main/java/org/Aayush/api/*, src/main/java/org/Aayush/routing/*, src/main/java/org/Aayush/core/id/FastUtilIDMapper.java | SpotBugs findings detected (mutable representation exposure, floating-point equality, dead local stores, constructor-throw, compareTo/equals mismatch, public primitive attributes, unread field, and naming issues) | HASH:97549551
  CRITICAL | TOOLING | taro-frontend/package.json | ESLint compact formatter unavailable; frontend lint command cannot run with --format compact | HASH:d4e35c90
  CRITICAL | TOOLING | scripts/check-api-contract.js | Required scan script missing | HASH:5d277d17

Regressions detected:
  none

Cycle       : 5
Scan ID     : 20260405_000934

Active findings after dedup:
  MAJOR | STATIC | mvn verify | State pool leak warning reported during verify: WARNING: 1 states leaked (extracted but not recycled). Replenishing pool. | HASH:ff524e36
  ADVISORY | TEST | mvn verify | Validation performance summary reported 1 warning | HASH:758d4160
  MINOR | STYLE | src/main/java/org/Aayush/routing/execution/* and src/main/java/org/Aayush/routing/overlay/LiveOverlay.java | Checkstyle violations detected (missing package-info, missing Javadocs, line-length, visibility, final-parameter, hidden-field, and magic-number rules) | HASH:4c2f737a
  MAJOR | STATIC | src/main/java/org/Aayush/api/*, src/main/java/org/Aayush/routing/*, src/main/java/org/Aayush/core/id/FastUtilIDMapper.java | SpotBugs findings detected (mutable representation exposure, floating-point equality, dead local stores, constructor-throw, compareTo/equals mismatch, public primitive attributes, unread field, and naming issues) | HASH:97549551
  CRITICAL | TYPE | src/main/python/learning/ingestion/contracts.py, src/main/python/learning/calibration/selector.py, src/main/python/learning/datasets/builder.py, src/main/python/tests/learning/test_temporal_feature_surface.py | mypy reported 11 type errors across 4 Python files | HASH:0d142fb4
  CRITICAL | TOOLING | taro-frontend/package.json | ESLint compact formatter unavailable; frontend lint command cannot run with --format compact | HASH:d4e35c90
  CRITICAL | TOOLING | scripts/check-api-contract.js | Required scan script missing | HASH:5d277d17

Regressions detected:
  none

Cycle       : 4
Scan ID     : 20260405_000521

Active findings after dedup:
  MAJOR | STATIC | mvn verify | State pool leak warning reported during verify: WARNING: 1 states leaked (extracted but not recycled). Replenishing pool. | HASH:ff524e36
  ADVISORY | TEST | mvn verify | Validation performance summary reported 1 warning | HASH:758d4160
  MINOR | STYLE | src/main/java/org/Aayush/routing/execution/* and src/main/java/org/Aayush/routing/overlay/LiveOverlay.java | Checkstyle violations detected (missing package-info, missing Javadocs, line-length, visibility, final-parameter, hidden-field, and magic-number rules) | HASH:4c2f737a
  MAJOR | STATIC | src/main/java/org/Aayush/api/*, src/main/java/org/Aayush/routing/*, src/main/java/org/Aayush/core/id/FastUtilIDMapper.java | SpotBugs findings detected (mutable representation exposure, floating-point equality, dead local stores, constructor-throw, compareTo/equals mismatch, public primitive attributes, unread field, and naming issues) | HASH:97549551
  CRITICAL | TOOLING | .venv/bin/python -m mypy | mypy CLI rejects the mandated -q flag; python type check cannot run | HASH:1c4bfde8
  CRITICAL | TOOLING | taro-frontend/package.json | ESLint compact formatter unavailable; frontend lint command cannot run with --format compact | HASH:d4e35c90
  CRITICAL | TOOLING | scripts/check-api-contract.js | Required scan script missing | HASH:5d277d17

Regressions detected:
  none

Cycle       : 3
Scan ID     : 20260405_000029

Active findings after dedup:
  MAJOR | STATIC | mvn verify | State pool leak warning reported during verify: WARNING: 1 states leaked (extracted but not recycled). Replenishing pool. | HASH:ff524e36
  ADVISORY | TEST | mvn verify | Validation performance summary reported 1 warning | HASH:758d4160
  MINOR | STYLE | src/main/java/org/Aayush/routing/execution/* and src/main/java/org/Aayush/routing/overlay/LiveOverlay.java | Checkstyle violations detected (missing package-info, missing Javadocs, line-length, visibility, final-parameter, hidden-field, and magic-number rules) | HASH:4c2f737a
  MAJOR | STATIC | src/main/java/org/Aayush/api/*, src/main/java/org/Aayush/routing/*, src/main/java/org/Aayush/core/id/FastUtilIDMapper.java | SpotBugs findings detected (mutable representation exposure, floating-point equality, dead local stores, constructor-throw, compareTo/equals mismatch, public primitive attributes, unread field, and naming issues) | HASH:97549551
  CRITICAL | TOOLING | pyproject.toml | mypy is not installed in .venv; python type check cannot run | HASH:c1760d3a
  CRITICAL | TOOLING | taro-frontend/package.json | ESLint compact formatter unavailable; frontend lint command cannot run with --format compact | HASH:d4e35c90
  CRITICAL | TOOLING | scripts/check-api-contract.js | Required scan script missing | HASH:5d277d17

Regressions detected:
  none

Cycle       : 2
Scan ID     : 20260404_235507

Active findings after dedup:
  MAJOR | STATIC | mvn verify | State pool leak warning reported during verify: WARNING: 1 states leaked (extracted but not recycled). Replenishing pool. | HASH:ff524e36
  ADVISORY | TEST | mvn verify | Validation performance summary reported 1 warning | HASH:758d4160
  CRITICAL | TOOLING | pom.xml | Maven SpotBugs plugin not configured; mvn checkstyle:check spotbugs:check cannot resolve prefix 'spotbugs' | HASH:a479496f
  CRITICAL | TOOLING | pyproject.toml | mypy is not installed in .venv; python type check cannot run | HASH:c1760d3a
  CRITICAL | TOOLING | taro-frontend/package.json | ESLint compact formatter unavailable; frontend lint command cannot run with --format compact | HASH:d4e35c90
  CRITICAL | TOOLING | scripts/check-api-contract.js | Required scan script missing | HASH:5d277d17

Regressions detected:
  none

Cycle       : 42
Scan ID     : 20260405_021751

Active findings after dedup:
  ADVISORY | TEST | mvn verify | Validation performance summary reported 1 warning | HASH:86103a35
  MINOR | STYLE | src/main/java/org/Aayush/routing/execution/ExecutionRuntimeBinder.java:[55,27] | VisibilityModifier: Variable 'heuristicProvider' must be private and have accessor methods. | HASH:3b249b35
  MINOR | STYLE | docs/agent/.scan_cycle42_java_static.log | 10597 additional Checkstyle violations remain in Java static analysis output (see log for line-level details) | HASH:65be7099

Regressions detected:
  none

Cycle       : 43
Scan ID     : 20260405_022016

Active findings after dedup:
  ADVISORY | TEST | mvn verify | Validation performance summary reported 1 warning | HASH:86103a35
  MINOR | STYLE | src/main/java/org/Aayush/routing/execution/ExecutionConfigAdminService.java:[4] | LineLength: Line is longer than 80 characters (found 85). | HASH:4a06f76a
  MINOR | STYLE | docs/agent/.scan_cycle43_java_static.log | 10596 additional Checkstyle violations remain in Java static analysis output (see log for line-level details) | HASH:a2e3e8ce

Regressions detected:
  none

Cycle       : 44
Scan ID     : 20260405_022258

Active findings after dedup:
  ADVISORY | TEST | mvn verify | Validation performance summary reported 1 warning | HASH:86103a35
  MINOR | STYLE | src/main/java/org/Aayush/routing/execution/ExecutionConfigAdminService.java:[11] | JavadocMethod: @return tag should be present and have description. | HASH:8fb27368
  MINOR | STYLE | docs/agent/.scan_cycle44_java_static.log | 10595 additional Checkstyle violations remain in Java static analysis output (see log for line-level details) | HASH:6e0f18b1

Regressions detected:
  none

Cycle       : 45
Scan ID     : 20260405_022527

Active findings after dedup:
  ADVISORY | TEST | mvn verify | Validation performance summary reported 1 warning | HASH:86103a35
  MINOR | STYLE | src/main/java/org/Aayush/routing/execution/ExecutionConfigAdminService.java:[18] | JavadocMethod: @return tag should be present and have description. | HASH:56697445
  MINOR | STYLE | docs/agent/.scan_cycle45_java_static.log | 10594 additional Checkstyle violations remain in Java static analysis output (see log for line-level details) | HASH:673f5636

Regressions detected:
  none

Cycle       : 46
Scan ID     : 20260405_022828

Active findings after dedup:
  ADVISORY | TEST | mvn verify | Validation performance summary reported 1 warning | HASH:86103a35
  MINOR | STYLE | src/main/java/org/Aayush/routing/execution/ExecutionConfigAdminService.java:[20] | LineLength: Line is longer than 80 characters (found 114). | HASH:1e64d4b1
  MINOR | STYLE | docs/agent/.scan_cycle46_java_static.log | 10593 additional Checkstyle violations remain in Java static analysis output (see log for line-level details) | HASH:669525f7

Regressions detected:
  none

Cycle       : 47
Scan ID     : 20260405_023117

Active findings after dedup:
  ADVISORY | TEST | mvn verify | Validation performance summary reported 1 warning | HASH:86103a35
  MINOR | STYLE | src/main/java/org/Aayush/routing/execution/ExecutionConfigAdminService.java:[21,36] | JavadocMethod: Expected @param tag for 'executionRuntimeConfig'. | HASH:6fdb3ba6
  MINOR | STYLE | docs/agent/.scan_cycle47_java_static.log | 10592 additional Checkstyle violations remain in Java static analysis output (see log for line-level details) | HASH:65d3f5eb

Regressions detected:
  none

Cycle       : 48
Scan ID     : 20260405_100848

Active findings after dedup:
  ADVISORY | TEST | mvn verify | Validation performance summary reported 1 warning | HASH:86103a35
  MINOR | STYLE | src/main/java/org/Aayush/routing/execution/ExecutionConfigAdminService.java:[27] | JavadocMethod: @return tag should be present and have description. | HASH:c556e61a
  MINOR | STYLE | docs/agent/.scan_cycle48_java_static.log | 10591 additional Checkstyle violations remain in Java static analysis output (see log for line-level details) | HASH:94cc7423

Regressions detected:
  none

Cycle       : 49
Scan ID     : 20260405_101340

Active findings after dedup:
  ADVISORY | TEST | mvn verify | Validation performance summary reported 1 warning | HASH:86103a35
  MINOR | STYLE | src/main/java/org/Aayush/routing/execution/ExecutionConfigAdminService.java:[29] | LineLength: Line is longer than 80 characters (found 111). | HASH:00db9e7a
  MINOR | STYLE | docs/agent/.scan_cycle49_java_static.log | 10590 additional Checkstyle violations remain in Java static analysis output (see log for line-level details) | HASH:ed1e6693

Regressions detected:
  none

Cycle       : 50
Scan ID     : 20260405_101655

Active findings after dedup:
  ADVISORY | TEST | mvn verify | Validation performance summary reported 1 warning | HASH:86103a35
  MINOR | STYLE | src/main/java/org/Aayush/routing/execution/ExecutionConfigAdminService.java:[30,36] | JavadocMethod: Expected @param tag for 'executionRuntimeConfig'. | HASH:5636e6e9
  MINOR | STYLE | docs/agent/.scan_cycle50_java_static.log | 10582 additional Checkstyle violations remain in Java static analysis output (see log for line-level details) | HASH:39644aa6

Regressions detected:
  none

Cycle       : 51
Scan ID     : 20260405_102117

Active findings after dedup:
  ADVISORY | TEST | mvn verify | Validation performance summary reported 1 warning | HASH:86103a35
  MINOR | STYLE | src/main/java/org/Aayush/routing/execution/DefaultExecutionRuntimeBinder.java:[16] | LineLength: Line is longer than 80 characters (found 84). | HASH:4acd36aa
  MINOR | STYLE | docs/agent/.scan_cycle51_java_static.log | 10581 additional Checkstyle violations remain in Java static analysis output (see log for line-level details) | HASH:a91b0ce9

Regressions detected:
  none

Cycle       : 70
Scan ID     : 20260405_150901

Active findings after dedup:
  ADVISORY | TEST | mvn verify | Validation performance summary reported 1 warning | HASH:86103a35
  MINOR | STYLE | src/main/java/org/Aayush/routing/execution/ExecutionProfileRegistry.java:[13,5] | JavadocVariable: Missing a Javadoc comment. | HASH:73ae0f40
  MINOR | STYLE | docs/agent/.scan_cycle70_java_static.log | 10562 additional Checkstyle violations remain in Java static analysis output (see log for line-level details) | HASH:ad4bb73c

Regressions detected:
  none
