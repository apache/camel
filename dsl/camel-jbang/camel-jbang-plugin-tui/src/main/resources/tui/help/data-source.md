# DataSource

Displays connection pool metrics for all `javax.sql.DataSource` beans
registered in the Camel registry. Supports **HikariCP** (Spring Boot default)
and **Agroal** (Quarkus default) connection pools.

## Table Columns

- **NAME** — The bean name of the DataSource in the registry
- **POOL** — The pool name (HikariCP pool name) or pool type (HikariCP / Agroal / Unknown)
- **ACTIVE** — Number of connections currently in use. Shown in red when equal to MAX (pool exhausted)
- **IDLE** — Number of idle connections available in the pool
- **TOTAL** — Total connections (active + idle) currently managed by the pool
- **MAX** — Maximum pool size configured. When ACTIVE reaches MAX, new connection requests will wait
- **WAITING** — Number of threads waiting for a connection. Shown in yellow when > 0
- **TYPE** — The Java class of the DataSource implementation

## Pool Exhaustion

When **ACTIVE = MAX**, the pool is exhausted — no idle connections remain.
New requests must wait for a connection to be released. The ACTIVE column
turns red to signal this condition. If WAITING also increases, the application
may experience timeouts.

## Connection Pools

### HikariCP
The default connection pool for Spring Boot. Known for its speed and small
footprint. Default max pool size is 10.

### Agroal
The default connection pool for Quarkus. Provides leak detection and
connection validation. Shows additional metrics like max-used and leak count.

## Keys

- `Up/Down` — select datasource
- `s` — cycle sort column
- `S` — reverse sort order
