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
- Optional: [vhs](https://github.com/charmbracelet/vhs) to convert `.tape` to `.gif`
  (`brew install vhs`)
- Optional: [asciinema](https://asciinema.org/) to play `.cast` files in the terminal
  (`brew install asciinema`)

## Live Tape Recording (Interactive)

You can record your live TUI session as a `.tape` file while using the TUI interactively.
The tape captures your keystrokes with timing, producing a script that can be replayed
or converted to an animated GIF.

### Starting and Stopping

There are two ways to toggle tape recording:

1. **Keyboard shortcut**: Press `Ctrl+R` to start/stop recording at any time
2. **Actions menu**: Press `F2` to open the actions menu, then select
   "Start Tape Recording" (or "Stop Tape Recording" if already recording)

When recording starts, a notification confirms "Tape recording started".
When recording stops, the tape is saved to the current directory as
`camel-tui-tape-<timestamp>.tape` and a notification shows the filename.

### Tips

- `Ctrl+R` is the quickest way to toggle recording. It is **not** captured
  in the tape itself, so the resulting script stays clean.
- The tape records the natural pauses between your keystrokes as `Sleep` commands,
  preserving the real-time feel.
- Keep recordings focused — record one specific workflow or feature at a time.

### Converting Tape to Animated GIF

The `.tape` file uses the [VHS](https://github.com/charmbracelet/vhs) format.
Install VHS and run:

```bash
vhs camel-tui-tape-20260525-143022.tape
```

VHS will replay the keystrokes in a virtual terminal and produce an animated GIF.
You can customize the output by editing the `.tape` file before converting — for
example, adding a title or adjusting sleep durations.

### Example Tape File

A recorded tape looks like this:

```
# My TUI Demo

Sleep 2s
Type "3"
Sleep 1.5s
Type "D"
Sleep 3s
Type "D"
Sleep 500ms
Type "1"
Sleep 1s
Type "q"
```

You can edit the file to add VHS directives at the top:

```
Output demo.gif
Set FontSize 14
Set Width 1200
Set Height 600

# My TUI Demo
Sleep 2s
Type "3"
...
```

See the [VHS documentation](https://github.com/charmbracelet/vhs) for all
available settings (font, dimensions, padding, themes, etc.).

## Scripted Recording (Headless)

Use the `record.sh` wrapper script for automated, repeatable recordings:

```bash
# Basic hello world demo (uses built-in example)
./record.sh camel-tui-hello --example=timer-log
```

The script starts a Camel integration in the background (`camel run --background`),
then launches `camel tui monitor --record=<tape>` which uses TamboUI's headless
recording backend. Interactions from the `.tape` file are played back automatically.
After recording, the background integration is stopped with `camel stop`.

## Playback

Play `.cast` files in the terminal:

```bash
asciinema play camel-tui-hello.cast
```

## Convert to GIF

There are two conversion paths depending on your source format:

### From .cast (Asciinema recording)

```bash
agg camel-tui-hello.cast camel-tui-hello.gif
```

### From .tape (VHS tape file)

```bash
vhs camel-tui-hello.tape
```

VHS produces a `.gif` by default. You can also produce `.mp4`, `.webm`,
or `.png` screenshots by adding `Output` directives to the tape file.

## Tape files

Tape files use the [VHS](https://github.com/charmbracelet/vhs) format to script
terminal interactions (`Type`, `Sleep`, `Screenshot`, etc.).

| File | Description |
|------|-------------|
| `camel-tui-hello.tape` | First impression — basic Hello World tour across all main tabs |

## Output

Screenshots are saved to `screenshots/` and `.cast` files are written
to the current directory.
