# Configuration

The Configuration tab shows all configuration properties of the running
integration. This provides a complete view of how the integration is
configured at runtime — including Camel settings, component options,
and application properties.

Properties can come from multiple sources and Camel merges them with
a defined priority order.

## Table Columns

- **KEY** — Property name following Camel's naming convention (e.g., `camel.main.name`, `camel.component.kafka.brokers`, `greeting.message`)
- **VALUE** — Current resolved property value. Sensitive values (passwords, tokens) are masked as `xxxxxx` for security
- **SOURCE** — Where the property was set:
  - `application.properties` — from the main properties file
  - `ENV` — from an environment variable
  - `JVM` — from a Java system property (`-D`)
  - `Spring Boot` — from Spring Boot configuration
  - `camel-component` — default from a Camel component
  - `override` — set programmatically in code
  - `initial` — set during context initialization

## Detail View

Press `Enter` on a property to see its documentation from the Camel
catalog. For `camel.main.*` and `camel.component.*` properties, the
detail panel shows:

- **Description** — what the property does
- **Type** — expected value type (string, boolean, integer, etc.)
- **Default** — default value if not explicitly set
- **Enum values** — allowed values for enumerated properties
- **Required** / **Deprecated** / **Secret** flags
- **Group** — the configuration group this property belongs to

## Keys

- `Up/Down` — select property
- `Enter` — view property detail / documentation
- `s` — cycle sort column
- `S` — reverse sort order
- `Esc` — close detail / back
