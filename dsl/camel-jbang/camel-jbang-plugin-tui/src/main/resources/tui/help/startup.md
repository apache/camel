# Startup

The Startup tab shows a timeline of how the integration started up:
a waterfall where each step's bar starts when the step started and is
as long as it took, so you see what ran inside what, and what ran
after what.

This is useful for optimizing startup time — for example, finding
components that take a long time to initialize, routes that are slow
to start, or external connections that delay readiness.

## Timeline Display

Each line shows one startup step with:

- **Duration** — Time in milliseconds this step took
- **Name** — What this step does (e.g., component initialization, route startup), indented under the step it is part of
- **Bar** — Where the step ran on the time line of the startup. Color ranges from green (fast) through yellow to red (slowest steps)

The steps are in the order they started. `s` puts the slowest first,
to find what to optimize without scrolling through the fast ones.

## Example Screen

```
  1234ms Starting CamelContext   ████████████████████████████████████████
   620ms   Start Routes                     ████████████████████
   412ms     timer-to-log                   █████████████
   150ms       Create Component: timer      █████
    80ms       Create Component: log             ███
   140ms     seda-consumer                               █████
   280ms   Start Consumers                                    █████████
```

In this example, the total startup took 1234ms. The slowest phase
was starting routes (620ms), with `timer-to-log` taking 412ms due
to component initialization. This helps you focus optimization
efforts on the right areas.

## What Causes Slow Startup

Common reasons for slow startup:

- **Component initialization**: Some components connect to external systems during startup (e.g., Kafka broker discovery, database connection pool warmup)
- **Route compilation**: Routes with many processors or complex EIP patterns take longer to compile
- **Bean creation**: Heavy beans with complex initialization logic
- **Class loading**: Large applications with many dependencies

## Startup Recorder

This tab requires the startup recorder to be enabled. Camel CLI
enables it by default in `dev` profile. For production deployments,
enable it with `camel.main.startup-recorder=true`.

The recorder captures timing data during startup and freezes after
the context is fully started — the data does not change during runtime.

## Keys

- `Up/Down` — scroll through steps
- `PgUp/PgDn` — scroll by page
- `Home/End` — jump to top/end
- `s` — the slowest steps first, or the order they started
- `F5` — reload startup data
- `Esc` — back
