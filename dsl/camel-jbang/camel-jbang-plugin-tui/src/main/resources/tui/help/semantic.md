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

Click **Load params** or press `Ctrl+p` to load the selected definition's parameters, including instructions.
The shortcut also works while editing; `p` remains available outside the editor.
This replaces the parameter draft with an editable copy labeled with its source; it keeps your text/state.
The definition's state selector is not applied. Without this action the form starts with expert defaults.
Click **Clear params** or press `Ctrl+k` to empty Instructions and all other parameter fields and rows,
keeping your text/state. Unset optional parameters use expert defaults; required parameters must be filled before Run.
Loading and clearing only change the editable draft, and are disabled while an evaluation is running.

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

Choose **Audit** to inspect retained evaluations and request outcomes. Result and Execution are separate.
The same formatter as the playground preserves fractional scores, empty label sets, declared meanings and
reported probability diagnostics. Historical interpretation is captured once; it is never rebuilt from current definitions.

- `/` edits retained-history filters: `category`, `expert`, `definition`, `operation`, `routeId` (entry),
  `executingRouteId`, `exchangeId`, `breadcrumbId`, `since` (inclusive) and `until` (exclusive).
  ISO-8601 time bounds select event/completion time; `since` must precede `until`.
  Supply `resultType=BOOLEAN|CHOICE|SCORE|CLASSIFICATION` with `resultValue` for exact Boolean/Choice or numeric Score
  equality, `resultLabel` for Classification membership, or inclusive `scoreMin`/`scoreMax` for Score ranges.
  For example: `definition=routeRequest resultType=CHOICE resultValue=none_of_these`.
  Quote spaces, for example `resultLabel="needs review"`. Missing/omitted results do not match value filters.
  `page.status`, `page.result`, `page.definition` refine this loaded page only.
- `c`, `e`, `t` select category, expert and recent-time presets. A time preset clears a fixed `until` bound.
- Up/Down select records. Scroll the mouse wheel over the **Expert evaluation** inspector to read more,
  or click it / press `Tab` to focus it, then use Up/Down or Page Up/Down. Home/End jump to the beginning/end.
  A scrollbar and line range show hidden content. `Tab` returns to the record list; scrolling the inspector
  keeps the selected evaluation unchanged. Dates and the display zone remain explicit.
- `n` loads older records; `g` goes to latest; `r` refreshes this page; `Space` pauses/resumes latest-page refresh.
  New arrivals are counted while selection remains pinned by event ID.
- `Enter` loads a source-local case. `[` / `]` choose an associated record and `Enter` inspects it; `Esc` returns.
  Breadcrumb/request/batch associations are not proven causality. Case completeness is always unknown.
- `u` explicitly inspects permitted input and its omission/redaction status. Input is untrusted message data.
- `p` creates a **new draft** from permitted captured input; it does not run inference. Original execution parameters
  were not captured, and current defaults/model revisions/route context may differ. Review the warning and parameters before Run.

The inspector retains full exchange and breadcrumb IDs; tables prioritize executing route, evaluation, expert, Result and Execution.
Health distinguishes capture settings, reader scope, retention, drops, evictions and delivery/query failures.
An active OpenTelemetry tracer does not prove an external collector received a trace.

`tui_get_audit` reads pages, caseEventId cases, eventIds exports or eventId details without moving the screen.
Case/export maxRecords is 1..1000 and input requires explicit includeInput. Detail input is labelled untrusted.
Generic `tui_get_table` Audit snapshots omit captured input even when the human inspector shows it.
`tui_set_input` supports audit.view, audit.filter, audit.page, audit.eventId and audit.case.

## Monitoring

Choose **Monitoring** for current measurements from the same cache used by CLI, MCP and optional JMX.
Reading performs no inference or audit-history scans. Historical queries, baselines, comparisons and alerts
belong to the external monitoring system.

- Click **Group** or press `g` to choose **Expert**, **Definition** or **All sources**.
  Expert totals include direct calls. Definition totals require separately collected named-definition measurements;
  direct calls have no definition. The switch selects supplied aggregates without combining counters or averaging means.
- Click the Definition, Expert or Availability filters, or press `a`, `x` or `s`, to cycle supplied values.
- `Tab` focuses the inspector; arrows and Page Up/Down scroll. `i` or `Enter` expands it to show
  measurement identity, dimensions and collection health. `Esc` restores the split view.
- `Space` freezes the display, including an in-flight refresh. Collection continues. Press it again to resume.
  `r` requests a refresh.

The framed table shows answers, failures and mean evaluation duration when supplied. The inspector shows
observation times, duration range and count, answer distributions or score buckets. Timings include successful
and failed executions with a recorded duration, excluding non-executed calls. They measure semantic evaluations,
not individual provider requests or distributed spans. Missing values say **not provided**.

The cache holds only the latest values for up to 100 series, sampled each second. It has no persistence or
recovery. Collection failures keep the last value and expose failure health. Observation times remain visible.
Correlation belongs to audit and tracing; no example audit-event IDs or comparison reports are retained here.

`tui_get_monitoring` accepts an optional source without changing the screen. `tui_set_input` supports
monitoring.view, monitoring.source, monitoring.aggregation (expert/definition/all), monitoring.definition,
monitoring.expert and monitoring.availability.
