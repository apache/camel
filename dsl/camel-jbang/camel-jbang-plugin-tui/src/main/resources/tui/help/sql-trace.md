# SQL Trace

Traces SQL query executions flowing through `camel-sql` and `camel-jdbc`
components. Captures individual executions with timing, row counts,
and failure status.

## KPI Strip

The top bar shows aggregate statistics:
- **Total** — Total number of SQL statements traced
- **Avg** — Average execution time in milliseconds
- **Slowest** — Longest single execution (yellow when >= 100ms)
- **Slow(>=100ms)** — Count of slow queries (yellow when > 0)
- **Failed** — Count of failed executions (red when > 0)

## Table Columns

- **TIME** — Timestamp of the execution
- **TYPE** — SQL type: SELECT, INSERT, UPDATE, DELETE, CALL, or OTHER
- **SQL** — The SQL query text
- **ROUTE** — The Camel route ID that executed the query
- **DURATION** — Execution time in ms (yellow when >= 100ms)
- **ROWS** — Row count (for SELECT) or update count (for INSERT/UPDATE/DELETE)
- **STATUS** — OK (green) or FAIL (red)

## Detail Panel

Select a statement with Up/Down to see full details below the table:
SQL text, endpoint URI, route, exchange ID, timing, and row counts.

## Keys

- `Up/Down` — select statement
- `PgUp/PgDn` — scroll detail panel
- `Home/End` — jump to top/end of detail
- `s` — cycle sort column
- `S` — reverse sort order
- `w` — toggle word wrap
