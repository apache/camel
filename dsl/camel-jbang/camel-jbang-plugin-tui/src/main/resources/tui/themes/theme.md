# Camel TUI Theme Reference

This document describes the CSS token system used by the Camel TUI.
Every `.tcss` theme file must define all 27 tokens listed below.
Missing tokens cause a startup validation error.

## File Format

Theme files use the `.tcss` (TamboUI CSS) format with standard CSS syntax.
Variables are declared with `$name: value;` and referenced as `$name`.

```css
$brand: #F69123;
#accent { color: $brand; }
#success { color: #4EC9B0; }
#selection { color: white; background: #264F78; text-style: bold; }
```

### Supported properties

| Property | Values |
|----------|--------|
| `color` | Hex (`#RRGGBB`), named (`white`, `black`), or variable (`$name`) |
| `background` | Same as `color` |
| `text-style` | `bold`, `dim`, `italic`, `underline`, `reversed` |

## Token Reference

### Brand & Chrome (10 tokens)

| Token | Type | Purpose |
|-------|------|---------|
| `accent` | fg | Brand color for focused borders, links, and highlights |
| `accent-bg` | fg+bg+bold | Inverted brand badge (e.g., active tab) |
| `hint-key` | fg+bg+bold | Key-hint chips in footers and prompts |
| `border` | fg | Unfocused panel borders |
| `border-focused` | fg | Focused panel borders |
| `title` | fg+bold | Panel and border titles |
| `selection` | fg+bg+bold | Row/item selection highlight |
| `row-alt` | bg | Zebra-stripe background for alternating rows |
| `base-bg` | bg | Main content area background |
| `base-fg` | fg | Default text foreground |

### Semantic Status (6 tokens)

| Token | Type | Purpose |
|-------|------|---------|
| `success` | fg | OK, running, passed, healthy |
| `warning` | fg | Caution, WARN log level, thresholds |
| `error` | fg | Failed, errors, stopped, ERROR log level |
| `muted` | fg | Disabled, secondary, placeholder text |
| `info` | fg | Informational accent (counts, prompts) |
| `notice` | fg | Secondary accent, typically purple (e.g., TRACE log level) |

### Content (3 tokens)

| Token | Type | Purpose |
|-------|------|---------|
| `label` | fg | Field labels, section headers, key hints, table headers |
| `change` | fg | Changed-value indicator in trace diffs |
| `search-match` | fg+bg | Search/find match highlight |

### Diagram (8 tokens)

| Token | Type | Purpose |
|-------|------|---------|
| `diagram-border` | fg | Box-drawing borders in route diagrams |
| `diagram-id` | fg | Route ID text |
| `diagram-from` | fg | "from" EIP nodes |
| `diagram-to` | fg | "to", "enrich", "marshal", "transform" nodes |
| `diagram-choice` | fg | "choice", "when", "otherwise" nodes |
| `diagram-action` | fg | "bean", "process", "log", "script" nodes |
| `diagram-eip` | fg | Routing EIPs (split, aggregate, multicast, etc.) |
| `diagram-default` | fg | Fallback for unknown EIP types |

## Design Guidelines

When creating a new theme:

- **Contrast**: ensure all `fg` tokens are readable against `base-bg`.
  Status colors (`success`, `error`, `warning`) must be distinguishable
  from each other and from `muted`.
- **Brand**: `accent` is Camel orange (`#F69123`) by convention but can
  be changed. `accent-bg` and `hint-key` should use the same brand color
  as background with a contrasting foreground (white or black).
- **Selection**: `selection` needs high contrast since it overlays any
  row content. Use a bold, distinct background.
- **Diagram colors**: the 7 EIP colors should be visually distinct from
  each other. They appear as foreground text on `base-bg`.
- **Zebra striping**: `row-alt` background should be subtle, just enough
  to distinguish alternating rows without clashing with `selection`.

## Built-in Themes

| Theme | File | Description |
|-------|------|-------------|
| `dark` | `dark.tcss` | VS Code-inspired dark palette (default) |
| `light` | `light.tcss` | GitHub-inspired light palette |
