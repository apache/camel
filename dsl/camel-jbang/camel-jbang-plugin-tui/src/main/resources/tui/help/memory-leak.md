# Memory Leak

This tab helps diagnose memory leaks by recording which objects
survive garbage collection and tracing why they are still alive.

It uses Java Flight Recorder (JFR) under the hood to sample
long-lived objects and capture their reference chains back to
GC roots. The recording is lightweight and safe for production.

For deep heap analysis, use traditional tools such as
**jmap** (heap dump), **jhat**, or **Eclipse MAT** alongside
this tab. This tab is for quick, in-place triage — those tools
give you the full picture.

## How To Use

1. Press **R** to start a recording (default 60 seconds)
2. Use **+**/**-** to adjust the duration before starting
3. Wait for the recording to complete (or press **X** to stop early)
4. Browse the results table and select entries to see details

## Table Columns

Samples from the same class and allocation site (stack trace) are
grouped together automatically.

- **#** — Group number
- **CLASS** — The class of the sampled long-lived object
- **COUNT** — Number of samples from the same allocation site
- **SAMPLED** — Sum of sampled allocation sizes during the recording
- **AGE** — Maximum age across samples in the group

## Detail Panel

Select an entry to see its reference chain and allocation stack trace:

- **Reference Chain** — Path from the object to its GC root, showing
  each referencing type and field name
- **Allocation Stack Trace** — Where the object was originally allocated

## Important: Sizes Are Sampled, Not Totals

The SAMPLED column shows the sum of allocation sizes that JFR captured
during the recording window — it is NOT the total heap footprint of
that class. Use the values to compare classes relative to each other
and to spot trends, not as absolute heap usage numbers.

## What To Look For

- **Objects with very long ages**: These have survived many GC cycles
- **Unexpected reference chains**: Objects held by caches, maps, or
  static fields that prevent garbage collection
- **Growing collections**: HashMap, ArrayList, ConcurrentHashMap entries
  that keep accumulating

## Dual Recording Mode (Default)

Dual mode runs two sequential recordings and compares them,
which is the most effective way to detect leaks. Press **d**
to toggle between **dual** and **single** mode.

In **dual** mode, pressing **r** runs:
- **Run 1** at the configured duration (e.g. 60s)
- **Run 2** at 2x the duration (e.g. 120s)

After both complete, a comparison table shows how each class
behaved across the two runs. The **RUN1** and **RUN2**
columns show the raw sampled sizes. For stable objects,
the sampled size stays roughly the same regardless of
duration. For leaking objects, the sampled size grows
because more allocations accumulate over the longer run.
The **GROWTH** column shows the percentage change.
Entries under 1KB in both runs are filtered out as noise.

### Trend Indicators

- **↑ leak!** (red) — Growth >= +50%%, very likely leak
- **↑ leak?** (yellow) — Growth +30%% to +50%%, suspicious
- **→ stable** (green) — Growth -30%% to +30%%, normal
- **↓** (dim) — Growth < -30%%, shrinking
- **new** (yellow) — Only appeared in Run 2
- **gone** (dim) — Only appeared in Run 1

### Low Confidence %s

A **%s** warning appears when sample counts are too low
(fewer than 5 in either run) or diverge significantly from
the expected duration ratio. The growth percentage is shown
with a **~** prefix (e.g. ~+53%%) to indicate the value may
not be reliable. JFR sampling is statistical — low sample
counts produce noisy results. Re-run with a longer duration
to collect more samples.

## Dominators View

Press **v** to toggle between the default **samples** view (grouped by
allocation class) and the **dominators** view. The dominators view
re-groups results by the **root holder** — the object closest to the
GC root in each reference chain. This answers the question: "which
Map, cache, or field is accumulating the most objects?"

Each row shows the holder (e.g. `LeakyCache.cache`), how many distinct
classes it retains, the total object count, and the aggregate sampled
size. Select a row to see the breakdown by allocation class, the
reference chain, and the allocation stack trace.

## Comparison With Heap Histogram

The **Heap Histogram** tab shows WHAT is using memory (class instance
counts and total sizes). This tab shows WHY objects are still alive
(reference chains to GC roots). Use both together: find suspicious
classes in Heap Histogram, then use Memory Leak to trace why they
are not being collected.

## Keys

| Key | Action |
|-----|--------|
| R | Start/restart recording |
| X | Stop recording early |
| d | Toggle single/dual recording mode |
| v | Toggle samples/dominators view |
| +/- | Adjust recording duration |
| Up/Down | Select sample |
| s | Cycle sort column (class, size, age) |
| S | Reverse sort order |
| m | Cycle minimum size filter |
| PgUp/PgDn | Scroll detail panel |
| Esc | Back |
