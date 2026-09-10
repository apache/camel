# Source

Browse the source files of the selected integration and view their content
with syntax highlighting. The tab has a left-right split layout with a file
explorer on the left and a source viewer on the right.

## File List (left panel)
- **Up/Down** — navigate files
- **Enter** — open file or directory
- **F4** — open file directly in edit mode
- **F12** — file actions menu (new file, new folder, rename, duplicate, delete, copy path)
- **Backspace** — go to parent directory

## Source Viewer (right panel)
- **Up/Down** — scroll through source code
- **F4** — edit local file (plain text; only when file is writable)
- **Esc** — cancel edit (in edit mode) or close viewer
- **Ctrl+S** — save file and continue editing (Camel dev mode auto-reloads)
- **F5** — save file and close editor (in edit mode)
- **Ctrl+R** — open refactoring menu in edit mode (YAML files only; choose an action for the current line)
- **Space** — cycle format (YAML/Java/XML) for Camel routes
- Quick documentation panel is shown at the bottom for Camel source files
- **/** — search in source
- **h** — highlight text
- **n/N** — next/previous match
- **w** — toggle word wrap
- **p** — toggle plain mode (hides line numbers, borders, and file panel for easy copy/paste)
- **Esc/c** — close source viewer

## Edit Mode (Shortcuts)
- **Ctrl+Z** — undo
- **Ctrl+Y / Ctrl+Shift+Z** — redo
- **Alt+Up / Alt+Down** — move YAML list block up/down
- **Ctrl+D** — duplicate current block
- **Ctrl+K** — delete current line
- **Ctrl+Left / Ctrl+Right** — word navigation
- **Home** — smart home (content indent, then column 0)
- Quick documentation panel is shown at the bottom (shows doc for current line)
- **F7** — show diff of unsaved changes
- **F9** — jump to next validation error

## Edit Mode (Tab Completion)
Press **F4** to enter edit mode, then **Tab** for context-aware completion:

**application.properties:**
- Key completion for `camel.main.*`, `camel.component.*`, `camel.dataformat.*`,
  and `camel.language.*` options from the Camel catalog
- Spring Boot auto-configuration properties (`server.*`, `spring.*`, `management.*`,
  etc.) resolved from starter JARs in the local Maven repository — works even when
  the application is not running (phantom/stopped projects)
- Value completion with enum choices, boolean values, Spring Boot value hints,
  and `{{placeholder}}` suggestions

**YAML DSL routes:**
- On `uri:` lines (or inline EIPs like `to:`, `from:`), Tab shows a list of
  Camel component names filtered by role: consumer endpoints (e.g. `from:`)
  exclude producer-only components, and producer endpoints (e.g. `to:`) exclude
  consumer-only components. Type to filter by name or label (e.g. "cloud",
  "messaging"). Selecting a component auto-inserts a `parameters:` block.
- Inside `parameters:` blocks, key completion shows endpoint options from the
  Camel catalog, filtered by consumer/producer role. Required options appear
  first (marked with `*`). Already-specified options are excluded.
- Inside EIP blocks (e.g. `split:`, `aggregate:`, `filter:`), Tab shows
  the EIP's configurable options (attribute-type only, excluding structural
  elements like `steps:` and `expression:`).
- Value completion shows enum choices, boolean values, and `{{placeholder}}`
  suggestions from your `.properties` files

Use **Up/Down** to navigate, **Enter** to accept, **Esc** to dismiss, and
type to filter the completion list.

## Route Jump Links
Lines with `to:`, `toD:`, `wireTap:`, or similar endpoints that reference
another route show a **↵ routeId** indicator. Press **Enter** on such a line
to jump to the target route's definition (within the same file or across files).
Reverse links are shown on `from:` lines, indicating which route calls this one.
Jump indicators are hidden in plain mode.

## Go to Route
- **g** — open a filterable popup listing all routes found in the source files.
  Type to fuzzy-filter by route ID or endpoint URI, then press **Enter** to
  navigate to the selected route.

## Go to Node / Line
- **Ctrl+G** — open a popup showing routes and their individual
  processors/EIPs in a tree structure. Type to fuzzy-filter by route ID,
  EIP type, or label, then press **Enter** to jump directly to the selected
  node in the source editor. Type a **line number** (e.g. `47`) and press
  **Enter** to jump directly to that line.

## General
- **Tab** — toggle focus between file list and source viewer
- The focused panel title is highlighted; the unfocused panel dims
- Drag the split border with the mouse to resize panels

## AI live edit

With `/write live` in the AI panel (`F8`), a change the AI makes is replayed
here instead of shown as a diff: the AI panel hides, the file opens in edit
mode and the change is typed hunk by hunk so you can follow it in context.

- **Enter** — continue with the next change
- **any other key** — finish the current change at once
- **F4** — edit yourself; the remaining changes wait
- **F9** — continue the AI changes after editing yourself (a change whose
  surrounding lines you edited is skipped and reported to the AI)
- **F8** — ask the AI about the current change: the AI panel opens with the
  question prefilled ("About edit 2 of 3: ..."), the answer comes back in the
  same turn, and closing the panel (`F8` or `Esc`) returns to the pause; if
  the AI revises the change it continues in the editor from where it is
- **Esc** — stop; what was typed stays in the editor
- then **Ctrl+S** / **F5** saves (this is the confirmation, dev mode reloads),
  **F7** shows the diff, **Esc** discards; the AI panel comes back afterwards
