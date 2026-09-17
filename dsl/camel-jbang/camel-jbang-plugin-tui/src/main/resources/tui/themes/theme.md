# Camel TUI Theme Reference

This document describes the CSS token system used by the Camel TUI.
Every `.tcss` theme file must define all 28 required tokens listed below.
Missing required tokens cause a startup validation error. The 7 syntax
tokens are optional.

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

### Content (4 tokens)

| Token | Type | Purpose |
|-------|------|---------|
| `label` | fg | Field labels, section headers, key hints, table headers |
| `change` | fg | Changed-value indicator in trace diffs |
| `search-match` | fg+bg | Search/find match highlight |
| `mnemonic` | fg+bold+underline | Keyboard shortcut mnemonic in tab headers and menus |

### Diagram (8 tokens)

| Token | Type | Purpose |
|-------|------|---------|
| `diagram-border` | fg | Box-drawing borders in route diagrams |
| `diagram-id` | fg | Route ID text |
| `diagram-from` | fg | `from` EIP nodes |
| `diagram-to` | fg | `to`, `enrich`, `marshal`, `transform` nodes |
| `diagram-choice` | fg | `choice`, `when`, `otherwise` nodes |
| `diagram-action` | fg | `bean`, `process`, `log`, `script` nodes |
| `diagram-eip` | fg | Routing EIPs (`split`, `aggregate`, `multicast`, etc.) |
| `diagram-default` | fg | Fallback for unspecified EIP types |

### Syntax (7 tokens, optional)

Code in the Source tab and in fenced code blocks of AI answers is highlighted
with these tokens. They are optional: a theme that omits them gets the
built-in Monokai palette (dark themes) or GitHub-inspired palette (light
themes). Define them when the theme calls for its own editor look, as the
Turbo Pascal theme does, but keep code readable.

| Token | Type | Purpose |
|-------|------|---------|
| `syntax-comment` | fg | Comments |
| `syntax-string` | fg | String literals, YAML and properties values, XML attribute values |
| `syntax-keyword` | fg | Keywords and modifiers, YAML and properties keys, XML tags |
| `syntax-function` | fg | Annotations, XML attribute names |
| `syntax-type` | fg | Primitive and built-in types |
| `syntax-constant` | fg | Numbers, booleans, null, XML entities |
| `syntax-text` | fg | Plain code text such as `:` and `=` separators |

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
- **Syntax tokens**: only override them when the theme has a distinct
  editor identity. Keywords, strings and comments must stay distinguishable
  from each other and from `base-fg`.

## Built-in Themes

| Theme | File | Description |
|-------|------|-------------|
| `dark` | `dark.tcss` | VS Code-inspired dark palette (default) |
| `light` | `light.tcss` | GitHub-inspired light palette |
| `dracula` | `dracula.tcss` | Dracula purple-and-pink-on-dark palette |
| `nord` | `nord.tcss` | Nord arctic, bluish dark palette |
| `nord-light` | `nord-light.tcss` | Nord arctic light palette |
| `solarized-dark` | `solarized-dark.tcss` | Solarized dark palette |
| `solarized-light` | `solarized-light.tcss` | Solarized light palette |
| `gruvbox-dark` | `gruvbox-dark.tcss` | Gruvbox retro-groove dark palette |
| `gruvbox-light` | `gruvbox-light.tcss` | Gruvbox retro-groove light palette |
| `catppuccin-mocha` | `catppuccin-mocha.tcss` | Catppuccin Mocha pastel dark palette |
| `catppuccin-frappe` | `catppuccin-frappe.tcss` | Catppuccin Frappé pastel dark palette |
| `catppuccin-latte` | `catppuccin-latte.tcss` | Catppuccin Latte pastel light palette |
| `tokyo-night` | `tokyo-night.tcss` | Tokyo Night neon-on-dark palette |
| `rose-pine` | `rose-pine.tcss` | Rosé Pine muted dark palette |
| `rose-pine-moon` | `rose-pine-moon.tcss` | Rosé Pine Moon softer dark palette |
| `kanagawa` | `kanagawa.tcss` | Kanagawa Japanese-wave-inspired dark palette |
| `everforest` | `everforest.tcss` | Everforest warm, green-forest dark palette |
| `everforest-light` | `everforest-light.tcss` | Everforest warm, green-forest light palette |
| `monochrome` | `monochrome.tcss` | Grayscale palette, no color, brightness only |
| `crt` | `crt.tcss` | Retro green-phosphor terminal palette |
| `turbo-pascal` | `turbo-pascal.tcss` | Borland Turbo Pascal IDE: yellow on blue, cyan frames, grey menu bar; overrides the syntax tokens |
