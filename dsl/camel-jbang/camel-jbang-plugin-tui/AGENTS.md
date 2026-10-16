# camel-jbang-plugin-tui: notes for coding agents

`camel tui`, the terminal UI for running Camel integrations, with the F8 AI panel and an embedded MCP server
(`camel tui --mcp`). It is built on TamboUI (`dev.tamboui`). User documentation:
`docs/user-manual/modules/ROOT/pages/camel-jbang-tui*.adoc`. The rules of the whole repository are in the root
`AGENTS.md`; this file adds what is particular to this module.

## Ground rules

- The TUI is developed on `main` only; its changes are not backported to the maintenance branches.
- Build with `mvn install -DskipTests` in this module, never with `-am`. A locally built `camel` loads the plugin
  from the local Maven repository, so `install` (not `compile`) makes a change visible; restart `camel tui` after.
- `mvn test` here runs about 2000 tests in some 40 seconds; `-Dtest=SomeTest` runs one class. It also formats the
  code (formatter and impsort run in `process-sources`): commit what it changes.
- To see a change on a running TUI, start it with `--mcp` and drive it with the `tui_*` MCP tools (`tui_get_options`
  first, then `tui_send_keys`, `tui_get_screen`, `tui_get_table`).
- Commits without a JIRA issue use `chore: camel-jbang - <what changed>`.

## Map

All classes are in the package `org.apache.camel.dsl.jbang.core.commands.tui` (the diagram widgets in `diagram`)
and use each other's package-private members, so there are no sub-packages. Classes are named by role:

| Class | What it is |
|---|---|
| `CamelMonitor` | the `camel tui` command: event loop, global keys, header, tab bar and footer, panels, overlays |
| `ProcessControl` | stops, kills and restarts the integrations, and sends their route commands (F10, the Overview tab, MCP) |
| `TabRegistry` | the tabs: the nine digit-key tabs, and the More tabs in `moreTabs` (one `MoreTab` entry each) |
| `MonitorTab`, `AbstractTab`, `AbstractTableTab` | what a tab implements, and its base classes |
| `*Tab` | one tab each |
| `MonitorContext` (`ctx`) | what the tabs share: the selected integration (`selectedPid`), settings, callbacks into CamelMonitor, `backgroundExecutor`, `runner`, `dataService`, `executeAction` to ask an integration and `fireAction` to tell it (a file per request since Camel 4.21, so requests cannot overwrite each other) |
| `DataRefreshService`, `StatusFileReader`, `StatusParser` | read the status files of the integrations (`~/.camel/<pid>-status.json`) into `IntegrationInfo` and the `*Info` classes |
| `*Popup`, `PopupManager` | popups; `PopupManager` has the switch integration (F3), More and confirm popups |
| `FilterListPopup`, `ChoicePopup`, `ActivityLogPopup`, `DialogHelper` | what popups are built on: a list narrowed down by typing, a short list of choices, a log with a detail panel, and centered confirm and input dialogs |
| `SourceTab`, `SourceViewer`, `SourceEditAssist`, `*Completions`, `*QuickDoc` | the source editor; its Tab completion is `SourceCompletion`, its Ctrl+R refactorings `SourceRefactorActions`, the live run data at the end of the lines `SourceLiveColumn` |
| `DiagramSupport`, `DiagramDetailSupport`, `ArchitectureView`, `diagram.*` | route and architecture diagrams; what they draw comes from `DiagramTopology` (`ctx.diagramTopology`), which asks an integration for the structure of its routes only when they change and takes the numbers from the status file |
| `AbstractDiagramTab` | what the Diagram and Routes tabs share of the route diagram: its state, keys, drilling into a route and back |
| `AiPanel`, `AiSlashCommandRegistry` | the F8 AI panel; its input line is `AiInputLine`, its tools and system prompt `AiToolSet`, its usage statistics `AiUsageStats`, and an external coding agent (ACP) `AcpSession` over `AcpAgentClient` |
| `LiveEdit`, `EditReplay` | `/write live`: a file an AI tool writes is typed into the source editor, to watch and then save or discard |
| `ToolConfirms`, `ToolConfirmPopup` | the AI tools run without asking (a developer tool); these are the few calls the user confirms first (a file write, a message to an endpoint the integration does not have or with a body read from a file outside the project, SQL that is not a SELECT, Groovy), and the popup that asks |
| `TuiMcpServer`, `TuiToolDefinitions`, `TuiToolRegistry`, `McpFacade` | the MCP server: the tools (`TuiTool`: schema, handler, flags), their implementations, the bridge to the screen; the shared file tools are `TuiFileTools`, which writes only to an integration's source directory, through the AI panel's write mode |
| `Theme`, `TuiIcons` | colours (themes in `src/main/resources/tui/themes`) and icons |
| `TuiSettings`, `SettingsPopup` | persisted settings |
| `TuiHelper` | shared helpers: text width, wrapping and truncation, ANSI, number and duration formats, footer hints |
| `ScreenSanitizer` | cleans every drawn frame of what a terminal would act on: control and BiDi characters in text from the integrations |
| `RecordingManager`, `TapePlayer`, `TapeRecorder` | `--record` and tapes |
| `TuiWebServer`, `WebPageHandler`, `WebTerminal` | `--web`, the terminal in a browser: the page (`src/main/resources/tui/web`), and a JLine terminal per browser session |

Seven classes are over 2000 lines (`AiPanel`, `SourceViewer`, `CamelMonitor`, `HistoryTab`, `DiagramSupport`,
`MemoryLeakTab`, `SourceEditAssist`): find the method with a search and read that range. `SourceSizeTest` keeps them,
and the methods over 150 lines, from growing: put a new feature in a new class rather than growing them, and look in
`TuiHelper` before writing a text or format helper.

## How a key travels

`CamelMonitor.handleEvent` offers a key to the overlays (help, caption, canvas), the shell panel (F6), the open
popups, the AI panel (F8), the global keys (`handleGlobalKeys`), and last to the active tab (`handleKeyEvent`).

- Esc never reaches a tab's `handleKeyEvent`. CamelMonitor calls the tab's `handleEscape()` first: close what the
  tab has open there (a field, a popup, a detail view) and return `true`, or Esc goes back to the Overview tab.
- The global keys are `q`, Ctrl+C, the digits, `?`, F1, F2, F3, F6, F8, F10, Ctrl+F and Ctrl+L. While the active
  tab's `isTextInputActive()` is `true`, `q`, the digits and `?` are typed into its field instead. A tab with a text
  field (a filter, a search, an editor, a popup that filters by typing) must override `isTextInputActive()`;
  `TabRegistryTest` fails when a tab holds one without it. A paste goes to the active tab's `handlePaste(String)`.
- A tab that needs a global key for its own editor returns `true` from `claimsKey(KeyEvent)` (the Semantic tab takes
  Ctrl+L, which elsewhere pins the log, while its input has the keyboard).
- The `/` filter of a table tab is `FilterField`; use it rather than writing another.

Each key a tab takes is written down in three places: the footer hint (`renderFooter`, with `TuiHelper.hint`), the
Keys section of the tab's help page (`src/main/resources/tui/help/<tab>.md`), and the user manual. Change all three;
`FooterKeysInHelpTest` fails when a key of the footer of a tab is missing from its help page. A footer too wide for
the screen drops the F-keys every screen has first, then the tab's hints from the tail (`FooterHints`): put the
important hints of a tab first.

## Threads and time

- Keys, mouse events, ticks and drawing run on the UI thread of TamboUI's `TuiRunner`. Do no file, network or
  `executeAction` work there.
- Slow work runs on `ctx.backgroundExecutor` (threads named `camel-tui-bg`) and hands its result back with
  `ctx.runner.runOnRenderThread(...)`; `BeansTab.loadBeans` shows the pattern, with an `AtomicBoolean` against a
  second load.
- The data refresh also runs on the background thread: it reads the status files, then calls
  `refreshWhileVisible()` of the tab on screen. A field written there and read when drawing must be `volatile` or
  atomic.
- Timers, timeouts and animations use `MonotonicClock.millis()`. The wall clock (`System.currentTimeMillis()`) can
  jump, and is only for showing a time of day.

## Tab lifecycle

| Method | When |
|---|---|
| `onTabSelected()` | the tab came on screen: fetch what it shows, or start a background load |
| `refreshWhileVisible()` | every data refresh while it is on screen, on the background thread; fetch with `ctx.dataService` |
| `onIntegrationChanged()` | another integration was selected: drop what belonged to the previous one |
| `ensureDataLoaded()`, `dataLoadError()` | for a tab that loads on demand, so an MCP client or the AI panel can wait for its data |
| `getTableDataAsJson()` | what the `tui_get_table` tool returns for the tab |

## Recipes

Add a More tab:

1. Write `XyzTab extends AbstractTableTab` (or `AbstractTab` when it is not one table) with a
   `(MonitorContext ctx)` constructor, `description()`, and `getHelpText()` returning
   `DocHelper.loadHelpText("xyz")`.
2. Write the help page `src/main/resources/tui/help/xyz.md` and list it in `HelpTextResourcesTest`.
3. Register the tab in `TabRegistry.initTabs`: create it and add a `MoreTab` to `moreTabs`, in its group, with an
   icon from `TuiIcons` (two columns wide) and a label with `&` before its shortcut letter. `requiredConsoles` and
   `activeWhen` decide when the tab is listed.
4. Implement `getTableDataAsJson()` for `tui_get_table`, and `isTextInputActive()` if the tab has a text field.
5. Test the drawing with `TuiTestHelper.renderToString(tab, width, height)`, and add the tab to the More list in
   `camel-jbang-tui.adoc`.

Add a `tui_*` MCP tool:

1. Add one entry to `TuiToolDefinitions`: `tools.add(tool(toolDef(name, description, properties), r::callXyz,
   flags...))`, with the schema, the handler and the flags together.
2. Write the `callXyz(Map<String, Object> args)` method in `TuiToolRegistry`. It reaches the screen through
   `McpFacade`, and through `McpFacade.MonitorBridge` (implemented in `CamelMonitor.createMcpFacade`) for what only
   CamelMonitor can do.
3. Flag it `READ_ONLY` when it changes nothing (`TuiToolRegistryCoreToolsTest` requires this of every
   `tui_get_*`), and `CORE` only when local models need it, as each core schema is sent with every AI panel request
   (`AiPanelPromptBudgetTest` guards their size).
4. The `camel_*` tools are shared with camel-jbang-mcp: they come from `ToolRegistry` in camel-jbang-core and are
   not defined here.

Add a popup: extend `FilterListPopup` for a list narrowed down by typing, `ChoicePopup` for a short list of choices
or `ActivityLogPopup` for a log, and use `DialogHelper` for a confirm or input dialog, rather than drawing a list, a
frame and a scrollbar again. A tab owns its popup, draws it at the end of `render`, offers it the keys first in
`handleKeyEvent`, closes it in `handleEscape()`, and reports it in `isTextInputActive()` when it takes typed text
(`AbstractDiagramTab` and its `GotoNodePopup` are an example).

## Tests

- New tests use AssertJ, and Awaitility instead of `Thread.sleep`.
- `TuiTestHelper.renderToString` draws a tab into a buffer; footers are tested by joining the spans of
  `renderFooter`.
- The key handling of `CamelMonitor` is tested by calling `handleGlobalKeys` through reflection with a mocked
  `TabRegistry` (see `MonitorTabTextInputTest`).
- Guard tests fail on the drift agents tend to leave behind: `TabRegistryTest` (a text field without
  `isTextInputActive()`), `FooterKeysInHelpTest` (a footer key missing from the help page),
  `TuiToolRegistryCoreToolsTest` (a `tui_get_*` tool not flagged `READ_ONLY`) and `SourceSizeTest` (a class or method
  growing past its size). Their messages say what to do.
