# Catalog

The Catalog tab shows Camel catalog artifacts (components, data formats,
languages, and others) and Kamelets. By default it shows only artifacts used by the
running integration (matched via Maven dependencies). Press `a` to toggle
to the full catalog showing all available artifacts.

For each Camel dependency in the integration, the tab cross-references
the Camel catalog to identify all artifacts provided by that dependency.
For example, `camel-core` provides the `direct`, `seda`, `timer`, `bean`,
`log`, and `mock` components, while `camel-kafka` provides the `kafka`
component.

The project's own Kamelets (its `*.kamelet.yaml` files) are always listed,
first and marked `project`, together with the Kamelets of the catalog its
routes use. The full catalog lists all the Kamelets of the Kamelet catalog.

## Table Columns

- **NAME** — The catalog artifact name (e.g., `kafka`, `timer`, `json-jackson`)
- **KIND** — The artifact type: `component`, `dataformat`, `language`, `other`, `eip` or `kamelet`
- **DESCRIPTION** — Short description of the artifact
- **LABEL** — Category labels (e.g., "messaging", "scheduling", "transformation"); for a Kamelet,
  `project` for the project's own, else its type (`source`, `action`, `sink`)

Deprecated artifacts are shown dimmed with a "(deprecated)" suffix.

## Mode

Press `a` to toggle between:

- **app only** — show only artifacts from the integration's dependencies (default)
- **all** — show the full Camel catalog

## Scope

Press `f` to cycle the scope filter:

- **all** — show all catalog artifacts (default)
- **component** — show only components
- **dataformat** — show only data formats
- **language** — show only expression languages
- **other** — show only miscellaneous artifacts
- **eip** — show EIPs detected from the running app's routes
- **kamelet** — show only Kamelets

## Filter

Press `/` to open the filter input. Type a search term and press
`Enter` to filter by substring match on name, title, or label.

## Documentation

Press `d` to open the full documentation for the selected artifact in
a scrollable viewer. The documentation is loaded from the Camel catalog
and converted from AsciiDoc to Markdown. Use `↑`/`↓`/`PgUp`/`PgDn` or
mouse scroll to navigate, and `Esc` to close.

For a **Kamelet**, the documentation shows its type, where it comes from
(the project file or the Kamelet catalog), how a route uses it, and its
properties: required, type, default, and description. They go under
`parameters:` of the `kamelet:` endpoint.

## Options

Press `o` to open the options viewer for the selected artifact. This
shows all configuration options in a structured format with types,
defaults, enum values, groups, and descriptions.

For **components**, the viewer has three tabs:
- **Component** — component-level options
- **Endpoint** — endpoint-level options
- **Headers** — message headers with constant names

For **data formats**, **languages**, and **others**, a single Options tab
is shown. For a **Kamelet**, `o` opens its documentation, whose
properties are its options. For **EIPs**, Options and exchange Properties tabs are shown.

Press `←`/`→` or `Tab` to switch tabs. Use `↑`/`↓`/`PgUp`/`PgDn` or
mouse scroll to navigate, and `Esc` to close.

## Keys

- `s` — cycle sort column (name, kind, description)
- `S` — reverse sort order
- `a` — toggle app only / full catalog
- `f` — cycle scope
- `d` — open documentation viewer
- `o` — open options viewer
- `/` — open filter
- `Esc` — clear filter or back
