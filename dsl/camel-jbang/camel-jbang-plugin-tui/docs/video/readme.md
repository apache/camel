# Camel TUI Demo Videos

Terminal demo recordings using TamboUI's built-in recording system.
This produces Asciinema `.cast` files with pixel-perfect rendering
(no differential rendering artifacts).

## Prerequisites

- [Camel JBang](https://camel.apache.org/manual/camel-jbang.html) installed
- [camel-jbang-examples](https://github.com/apache/camel-jbang-examples) cloned
  (for the circuit-breaker, openapi, and routes demos)
- Optional: [agg](https://github.com/asciinema/agg) to convert `.cast` to `.gif`
  (`brew install agg`)
- Optional: [asciinema](https://asciinema.org/) to play `.cast` files in the terminal
  (`brew install asciinema`)

## Recording

Use the `record.sh` wrapper script:

```bash
# Basic hello world demo (uses built-in example)
./record.sh camel-tui-hello --example=timer-log

```

The script starts a Camel integration in the background (`camel run --background`),
then launches `camel tui monitor --record=<tape>` which uses TamboUI's headless
recording backend. Interactions from the `.tape` file are played back automatically.
After recording, the background integration is stopped with `camel stop`.

## Playback

```bash
asciinema play camel-tui-hello.cast
```

## Convert to GIF

```bash
agg camel-tui-hello.cast camel-tui-hello.gif
```

## Tape files

Tape files use the [VHS](https://github.com/charmbracelet/vhs) format to script
terminal interactions (`Type`, `Sleep`, `Screenshot`, etc.).

| File | Description |
|------|-------------|
| `camel-tui-hello.tape` | First impression — basic Hello World tour across all main tabs |

## Output

Screenshots are saved to `screenshots/` and `.cast` files are written
to the current directory.
