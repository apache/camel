# Type Converters

The Type Converters tab shows all type converters registered in the
Camel type converter registry. Type converters are used to automatically
convert message bodies, headers, and other values between Java types
during routing.

Camel ships with ~230 built-in (core) type converters that handle
common conversions like String to Integer, byte[] to InputStream,
Document to String, etc. Components can also register additional
converters for their own types.

## Table Columns

- **FROM** — Source Java type
- **TO** — Target Java type
- **KIND** — `core` (shipped with Camel) or `custom` (added by components or user code)

## Scope Modes

Press `f` to cycle through scope modes:

- **all** — show all type converters
- **non-jdk** — hide converters where both types are JDK classes (java.*, javax.*, org.w3c.*, org.xml.*)
- **custom** — only converters added by custom components or user code
- **core** — only Camel core converters (shipped with Apache Camel)

## Keys

- `Up/Down` — select converter
- `s` — cycle sort column
- `S` — reverse sort order
- `f` — cycle scope filter
- `/` — text filter by class name
- `Esc` — clear filter / back
