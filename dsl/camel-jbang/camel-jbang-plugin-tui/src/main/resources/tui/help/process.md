# Process

The Process tab shows the operating system process the selected
integration runs in: who runs it, which Camel and Java it runs on, where,
for how long, and the full command line that started it.

This is useful to check which JVM, Camel version or profile an app really
runs with, or to copy its command line to run it again by hand.

## Fields

- **PID** — The process id
- **User** — The operating system user that runs the process
- **Name** — The name of the integration
- **Camel** — The Camel version
- **Platform** — The runtime (Camel Main, Spring Boot, Quarkus) and its version
- **Profile** — The profile it runs with (dev, prod, ...)
- **Java** — The Java version, vendor and VM
- **Directory** — The working directory of the process
- **Uptime** — How long it has been running

Below the fields, **Command Line** shows the command that started the
process: the java executable, the JVM options and the arguments.

## Keys

- **w** — Wrap the command line, or show one argument per line
- **PgUp / PgDn** — Scroll
