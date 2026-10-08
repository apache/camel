# Source

Browse the source files of the selected integration and view their content
with syntax highlighting. The tab has a left-right split layout with a file
explorer on the left and a source viewer on the right.

## File List (left panel)
- **Up/Down** — navigate files
- **Enter** — open file or directory
- **F4** — open file directly in edit mode
- **F12** — file actions menu (new file, new folder, rename, duplicate, delete, copy path,
  and for a route file, convert to YAML, XML or Java: a new file next to it,
  without running it, with what did not carry over noted at its top)
- **Backspace** — go to parent directory

## Source Viewer (right panel)
- **Up/Down** — scroll through source code
- **F4** — edit local file (plain text; only when file is writable)
- **Esc** — cancel edit (in edit mode) or close viewer
- **Ctrl+S** — save file and continue editing (Camel dev mode auto-reloads)
- **F5** — save file and close editor (in edit mode)
- **Ctrl+R** — open refactoring menu in edit mode, for the current line: replace
  the endpoint URI, extract the value at the cursor to a property, extract a step
  to a new route file (YAML and XML)
- **Space** — cycle format (YAML/Java/XML) for Camel routes
- Quick documentation panel is shown at the bottom for Camel source files
  (YAML, XML and Java DSL routes: the component and options of an endpoint,
  the EIP of a step, the language of an expression). Before it: the values of
  the `{{placeholders}}` of the line from the project's .properties files, and
  where the bean the line refers to is declared
- The problems of a Camel file are marked as soon as it opens: a red ✗ on
  their lines and the count in the title; the panel at the bottom says the
  problem of the selected line, and **F9** goes to the next one
- **u** — usages: the routes that consume from the endpoint of the line and the
  steps that send to it (direct:, seda:...), across the project; **Enter** goes there
- **/** — search in source
- **h** — highlight text
- **n/N** — next/previous match
- **w** — toggle word wrap
- **p** — toggle plain mode (hides line numbers, borders, and file panel for easy copy/paste)
- **Esc/c** — close source viewer

## Live Run Data
While the integration runs, a column after the line numbers shows what the
processors on each line do: the exchanges they handled (`79`), how many failed
(`✗10`, in red), and the mean time when it is 1 ms or more. The column keeps
its width, so the code does not move as the numbers grow. The source reads as
a heat map of the route: where messages go, where they fail.

## Edit Mode (Shortcuts)
- **Ctrl+Z** — undo
- **Ctrl+Y / Ctrl+Shift+Z** — redo
- **Alt+Up / Alt+Down** — move YAML list block up/down
- **Ctrl+D** — duplicate current block
- **Ctrl+K** — delete current line
- **Ctrl+Left / Ctrl+Right** — word navigation
- **Home** — smart home (content indent, then column 0)
- Quick documentation panel is shown at the bottom (shows doc for current line;
  in a simple expression, the function, header or operator the cursor is on;
  in XML, the element or attribute the cursor is on)
- **F7** — show diff of unsaved changes
- **F9** — jump to next validation error
- **Shift+F8** — ask the AI to fix the problem on the cursor line: the file is
  saved as it is, and the AI panel opens with the question in its input (file,
  line, problem); press Enter to send it, or change it first
- **Shift+F9** — apply the fix of the problem on the cursor line, when the problem
  says it (an option typo, an enum value a letter off, `to` that should be
  `toD`, `${key}` where `{{key}}` is meant, a Simple function the error names
  the right one of, such as `${bdy}` → `${body}`); the Error panel shows the fix
- Java and XML DSL routes are checked as you type, like YAML routes: endpoint
  options, simple expressions, and a `to` with `${...}` that should be a `toD`.
  The problems are marked on their lines; an XML file with problems is not saved
  (as YAML), a Java file is saved and the problems are said.

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

**Java and XML DSL routes:**
- In the endpoint uri of `from`, `to`, `toD`, `wireTap`, `enrich`, `pollEnrich`
  and `poll` (the string given to them in Java, their `uri` attribute in XML),
  Tab completes the component name before the `:`, the endpoint options after
  `?` or `&` (`&amp;` in XML; filtered by consumer/producer role, already given
  ones left out), and the value of an option after `=`

**Java DSL routes:**
- After a dot in a route chain, Tab lists the methods that compile there: the
  options of the EIP the chain is on first (`.split(body()).` offers
  `parallelProcessing`, `streaming`...), then the EIPs, and the `end()`,
  `endChoice()` or `endDoTry()` that closes the block you are in
- The method is inserted with its parentheses, the cursor inside them when it
  takes arguments; the documentation comes from the catalog
- In an argument, the chain of the argument (`.filter(header("x").` offers
  `isEqualTo`, `isNotNull`...); also the REST DSL (`rest("/api").get(..).`),
  `restConfiguration()` and route templates
- Light help for hand-written edits: routes in variables and the code of
  lambdas are not completed; an AI coding agent helps with more: the F8 AI
  panel, or any agent that speaks ACP

**XML DSL routes:**
- After `<`, or on an empty line, Tab lists the elements that go inside the
  parent element (the EIPs of a route, `when` and `otherwise` in a `choice`,
  the languages where an expression goes); the chosen one is inserted with its
  required attributes and its end tag (`<to uri=""/>`, `<split></split>`)
- In a start tag, Tab lists the element's attributes, the required ones first,
  without the ones already given; in an attribute value, its values (enums,
  `true`/`false`, `{{placeholders}}`)
- The structure and documentation come from the XML schema of the catalog

**Simple expressions (YAML, Java and XML routes):**
- After `${`, Tab lists the functions of the simple language, with their
  parameters and examples; the chosen one is inserted as it is written
  (`${body}`, `${date:`, `${random(`)
- After `${header.` (also `exchangeProperty.` and `variable.`), Tab lists the
  names the file sets or reads, then the headers of the components it uses
- After a function and a space, Tab lists the operators: comparisons and
  `&&` `||` where the EIP takes a predicate (`when`, `filter`, `validate`,
  `onWhen`...), chaining (`~>`) and the default value (`?:`) elsewhere
- In the arguments of a function: the commands after `${date:` (`now`,
  `exchangeCreated`, `header.`...) and date patterns after the next `:`, the
  time zones of `date-with-timezone`, the project's beans after `${bean:`, the
  keys of its `.properties` files after `${properties:`

Use **Up/Down** to navigate, **Enter** to accept, **Esc** to dismiss, and
type to filter the completion list (an exact or prefix match comes first).

## Route Jump Links
Lines with `to:`, `toD:`, `wireTap:`, or similar endpoints that reference
another route show a **↵ routeId** indicator. Press **Enter** on such a line
to jump to the target route's definition (within the same file or across files).
Reverse links are shown on `from:` lines, indicating which route calls this one.
This works for YAML, XML and Java DSL routes, across all the folders of the
project and files of different DSLs (build output and `src/test` are left out);
Java routes are read without compiling them. The case and otherwise of a
switch link like a `to`. Jump indicators are hidden in plain mode.

A line that refers to a bean (`bean:name`, `.bean(MyBean.class)`, `ref: name`,
`#class:com.foo.MyBean`...) shows a **↵ name** indicator when the project
declares it (`@BindToRegistry`, `@Named`, `@Component`, `@Bean`, or the beans of
a YAML or XML file); **Enter** goes to its declaration.

## Go to Route
- **g** — open a filterable popup listing all routes of the project's source files.
  Type to fuzzy-filter by route ID or endpoint URI, then press **Enter** to
  navigate to the selected route.

## Go to Node / Line
- **Ctrl+G** — open a popup showing routes and their individual
  processors/EIPs in a tree structure. Type to fuzzy-filter by route ID,
  EIP type, or label, then press **Enter** to jump directly to the selected
  node in the source editor. Type a **line number** (e.g. `47`) and press
  **Enter** to jump directly to that line.

## Route Tree
- **Ctrl+T** — show or hide the route tree at the top right of the source:
  the route the cursor is in, from its `from` through each step, with the
  branches indented and the step under the cursor marked with ▶. It follows
  the cursor as you move and edit, for YAML, XML and Java routes, also when
  the route does not run. The **Route Tree** setting (F2 → Settings) says
  whether it is shown at first. It needs an editor of 90 columns or more.

## General
- **Tab** — toggle focus between file list and source viewer
- The focused panel title is highlighted; the unfocused panel dims
- Drag the split border with the mouse to resize panels

## AI live edit

With `/write live` in the AI panel (`F8`), a change the AI makes is replayed
here instead of shown as a diff: the AI panel hides, the file opens in edit
mode and the change is typed hunk by hunk so you can follow it in context
(a large change is typed faster, a few seconds at most).

- **Enter** — continue with the next change
- **any other key** — finish the current change at once
- **F4** — edit yourself; the remaining changes wait
- **F9** — continue the AI changes after editing yourself (a change whose
  surrounding lines you edited is skipped and reported to the AI)
- **F8** — ask the AI about the current change: a compact AI panel opens with
  the question prefilled ("About edit 2 of 3: ..."), the answer comes back in the
  same turn, and closing the panel (`F8` or `Esc`) returns to the pause; if
  the AI revises the change it continues in the editor from where it is
- **Esc** — stop; what was typed stays in the editor
- then **Ctrl+S** / **F5** saves (this is the confirmation, dev mode reloads),
  **F7** shows the diff, **Esc** discards; the AI panel comes back afterwards
- after five minutes without saving or discarding, the AI stops waiting; the
  edit stays here and the AI is told what you did with it next time you ask
