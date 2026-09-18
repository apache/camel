# Beans

The Beans tab shows all beans registered in the Camel registry. Beans
are reusable Java objects that routes can reference by name — for
example, a database connection pool, a custom processor, a REST
configuration, or a type converter.

In Camel, beans can be registered in several ways:

- **YAML DSL** — defined in a `beans:` section of your YAML route file
- **Java** — bound to the registry via `bindToRegistry()` or annotations
- **Spring/Quarkus** — injected as managed beans (`@Component`, `@Named`)
- **Camel auto-discovery** — components and languages auto-register their beans

## Table Columns

- **NAME** — Bean name used to look it up from routes. In a route you reference this with `.bean("myService")` or `${bean:myService}`
- **TYPE** — Short Java class name of the bean (e.g., `Greeter`, `HikariDataSource`)

## Detail View

The detail panel at the bottom shows the full type (including package)
and the bean's properties with their current values. This is useful for
verifying configuration — for example, checking that a database connection
pool has the correct URL, or that a data format is configured with the
right options.

## Filter Modes

Press `f` to cycle through filter modes:

- **all** — show all beans (including framework internals)
- **user** — only your application beans (excludes Camel, Spring, Quarkus, and JDK beans)
- **camel** — only Camel internal beans
- **spring** — only Spring Framework beans (Spring Boot apps only)
- **quarkus** — only Quarkus beans (Quarkus apps only)

## Keys

- `Tab` — switch focus between table and detail panel
- `Up/Down` — navigate in focused panel
- `PgUp/PgDn` — page in focused panel
- `s` — cycle sort column
- `S` — reverse sort order
- `f` — cycle filter
- `Esc` — back
