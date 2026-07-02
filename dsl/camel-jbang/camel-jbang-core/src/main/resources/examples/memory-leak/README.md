## Memory Leak

This example simulates a memory leak for testing the JFR Old Object Sample diagnostic tool.

It runs three routes:
- **cache-leak** — adds a 64 KB entry to a HashMap every 200ms (never evicts)
- **buffer-leak** — appends a 32 KB byte array to a List every 300ms (never shrinks)
- **healthy** — a normal route with no leak, for comparison

### How to run

    camel run memory-leak.java

### Diagnose with TUI

    camel tui

Navigate to the **JFR Old Objects** tab and press **R** to start a dual recording.
After both runs complete, the comparison table will flag `byte[]` and `HashMap$Node`
as `growing` with high growth ratios, confirming the leak.

### Diagnose with CLI

    camel cmd jfr-old-objects
