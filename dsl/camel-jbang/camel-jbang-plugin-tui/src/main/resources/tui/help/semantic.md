# Semantic

Inspect the running application's published semantic definitions and expert contracts.
Reading metadata does not run an inference. Unsaved source edits are not shown.

## Views

- **Definitions** — table and detail pane with state selector, parameters and operation contract.
- **Experts** — compact expert list and operation contract on the left; definitions using it and a larger direct expert playground on the right.
- **Relationships** — definitions joined to their expert operations. Shared operations share a node.
  Cards include state, input/result types and reuse counts. Labeled arrows show references. The map scrolls to keep the selected definition visible. Below 120 columns, it fills the view.

The views share the selected definition and expert. Selecting a definition also selects its operation
in the expert contract and playground. Click the operation strip or use `[` / `]` to select another operation.
The linked-definition highlight follows a matching operation, or clears when no definition uses it.

## Keys

- `v` — cycle Definitions, Experts, Relationships and Audit
- `Tab` — focus the next pane
- `Up/Down`, `PgUp/PgDn` — navigate the focused pane
- `Home/End` — first/last declaration
- `Enter` — follow the expert or return to its selected definition
- `/` — filter published metadata, including parameter values and errors; `Esc` clears the filter
- `r` — refresh runtime metadata
- `[` / `]` — previous/next expert operation in Experts
- `e` — open a sample for the selected definition, in any view
- `t` — edit Try expert directly, in Experts
- `p` — copy the selected definition's parameters into Try expert, in Experts

## Direct expert playground

In Experts, press `t` to edit the playground. When an operation publishes a free-text `instructions`
parameter, it appears as a separate plain-text **Instructions** editor on the left, above **Text to assess**
(or **State to assess** in JSON mode). Enter the question in Instructions and the content to evaluate below it.
Criteria and other parameters stay on the right. Operations without instructions use a single Input editor.
No definition or route is needed.
`Ctrl+r` or the Run button invokes the selected expert operation; `Enter` adds a new line.
`Tab` / `Shift+Tab` move through Instructions (when present), text/state, each parameter control, and output. Tab past output returns to the expert list.
`Ctrl+l` clears the current input field. `Esc` leaves editing and keeps the text and result.

`Ctrl+t` switches text/JSON while editing text/state when both are accepted. Instructions stay plain text. JSON state must be an object or array.
`Ctrl+e` expands/restores the playground. Parameters are named controls derived from the contract:
text, numbers, Boolean/allowed-value selectors, key/value rows, and ordered lists.
Required fields are marked; unset optional fields use expert defaults.
Use `Ctrl+n` or **Add entry** for a new row, `Ctrl+d` to remove a row, and `Ctrl+↑↓` to reorder.
List indices start at zero. Use `←→`, Space or Enter to cycle a selector.
`Ctrl+l` clears a field or returns a selector to its unset state. The focused field stays visible when scrolling.
Nested map/list/object values still use JSON within their individual value field.
For long contracts, Tab to the contract pane and scroll with Up/Down.
The output shows the typed answer, its meaning, timing and returned evidence. Tab to output
and scroll to see longer results. Changed input is marked until run again.
When the expert links its score to an ordered-level parameter, the configured range and level descriptions
appear in definition details and results. Fractional scores name their adjacent levels; probability bars
use the descriptions. Editing levels does not relabel the previous result. Experts without this relationship
keep their published supported bounds and original probability labels.

Input is literal text, including `${...}` or `{{...}}`; state selectors and routes are not executed.
Normal contract and provider validation apply. Structured-only operations start in JSON mode.
A separate draft and last result are kept for each expert and operation until the integration changes.
A changed operation contract starts a new draft. Probability distributions and metadata appear below
the typed result; expand the playground or focus Output and scroll to inspect longer answers.
Failures/timeouts keep your draft and show an inline error; `Ctrl+r` retries explicitly.
Run is disabled when the selected application disconnects. Listing an expert does not prove its service is reachable;
no inference is made just to test availability.

Press `p` outside the editor to load the selected definition's parameters, including instructions.
This replaces the parameter draft with an editable copy labeled with its source; it keeps your text/state.
The definition's state selector is not applied. Without this action the form starts with expert defaults.

## Definition sample evaluation

Edit a JSON object with `body`, `headers` and `variables`.
The selected definition's state expression reads from this sample exchange.
Headers and variables must be objects. Variables are local to the sample;
repository names such as `global:name` are rejected.

`Ctrl+r` explicitly calls the configured expert with normal validation.
The sample is not sent through application routes. The result includes the typed
value, probability/confidence when available, metadata, elapsed time or an error.

`Tab` / `Shift+Tab` focus input or result; arrows scroll the result. `Ctrl+l` clears the sample while editing.
`Esc` closes the form. Reopening retains the sample and result for that definition until the integration changes.
Changed input or definition configuration is marked until run again. Results use the same presentation as Try expert.
Closing keeps an in-progress call. Evaluations time out after 50 seconds and request cancellation.
Abandoned file requests and WebSocket disconnects also request cancellation. Providers must cooperate
with interruption; remote or native inference may continue, so configure provider timeouts too.


MCP tools can read the visible draft and result with `tui_get_table`. Use `tui_set_input`
with `input`, `inputMode` (`text`/`json`), or `parameter.<name>` in Experts. Map/list values
use JSON and populate the normal controls. For an open sample popup, use `sample` (exchange
JSON), `sample.body` (text), or `sample.headers` / `sample.variables` (JSON objects).
Reading or editing never runs the expert; Ctrl+r explicitly evaluates the draft.


## Audit history

Choose **Audit** with `v` to browse retained evaluation evidence and explicit route
decisions. Capture runs in the application while the TUI is disconnected. Audit settings
come from the semantic DSL; expert overrides take precedence over the master default.
Auditing and OpenTelemetry are independent.

- `/` edits exact `field=value` filters, separated by spaces. Available fields are
  `category`, `action`, `expert`, `routeId`, `namespace`, `correlationId` and `since`.
  Example: `expert=security since=2026-10-09T12:00:00Z`.
- Up/Down select a record. `Tab` or Enter focuses the inspector; arrows scroll its details.
- `n` loads older records; `g` returns to the latest page; `r` refreshes the current page.
- Esc leaves details or clears the current filter.

Wide terminals show timestamp, action, category, operation, target, namespace, reason and
correlation columns. Smaller terminals move fields into the inspector. A route action is
only present for an explicit decision. Linked evaluations retain their captured result
meaning; missing/evicted evidence is shown as unavailable. Backend status shows the master
and expert settings, reader, evictions, drops, errors and whether a Camel OpenTelemetry
tracer is active. This does not assert that an external collector received a trace.

The MCP tool **`tui_get_audit`** reads history without moving the screen or changing its filters or selection.
Pass `eventId` alone to inspect a record and its evidence. For pages, pass `category`, `action`, `expert`,
`routeId`, `namespace`, `correlationId`, `since`, `limit` and a returned `cursor` as needed.
`tui_get_table` reads the visible Audit screen; `tui_set_input` changes it explicitly.
Audit refreshes are coalesced while a query is pending; changed filters use the latest submitted value.
