# Heap Histogram

The Heap Histogram tab shows class-level memory usage in the JVM heap,
similar to `jcmd <pid> GC.class_histogram`. Each row represents a class
with the number of live instances and total bytes consumed.

This is useful for diagnosing memory leaks, finding unexpected object
retention, and understanding which classes dominate heap usage.

## Table Columns

- **#** — Rank by bytes (from the raw JVM histogram)
- **CLASS NAME** — Fully qualified class name. Array types use JVM notation (e.g., `[B` = byte array, `[Ljava.lang.Object;` = Object array)
- **INSTANCES** — Number of live instances of this class on the heap
- **BYTES** — Total bytes consumed by all instances of this class

The title bar shows total classes, instances, and bytes for the current filter.

## Detail Panel

The detail panel below the table shows additional context for the selected class:

- **Class** — Full class name, package, instance count and bytes
- **Package Summary** — Total classes, instances, and bytes for all classes in the same package
- **JAR** — The Maven artifact (groupId:artifactId:version) and file path of the JAR containing the class. JDK classes show "JDK (built-in)"

## Filter Modes

- **all** (default) — Show all classes
- **non-jdk** — Exclude JDK classes (java.*, javax.*, jdk.*, sun.*, com.sun.*, arrays)
- **camel** — Show only classes from `org.apache.camel` packages

## What To Look For

- **Large byte counts at the top**: Normal for byte arrays and char arrays — these back Strings and buffers
- **Unexpected classes with high counts**: May indicate a memory leak
- **Growing instance counts on refresh**: Press F5 repeatedly to spot classes whose counts keep growing
- **Package summary**: Use the detail panel to see total memory for an entire package
- **JAR origin**: Identify which dependency owns the memory-heavy classes

## Keys

| Key | Action |
|-----|--------|
| Up/Down | Select class |
| s | Cycle sort column (className, instances, bytes) |
| S | Reverse sort order |
| f | Toggle filter (all / non-jdk / camel) |
| F5 | Refresh heap histogram |
| PgUp/PgDn | Scroll by page |
| Esc | Back |
