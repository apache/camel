# camel-jbang-core: notes for coding agents

The commands of the Camel CLI (`camel run`, `export`, `get`, `cmd`, `catalog`, `ask`, ...). The rules of the whole
repository are in the root `AGENTS.md`; this file adds what is particular to this module.

## Tests

The tests run in 4 forks at the same time, and each fork runs many test classes one after the other (the forks are
reused). So a test must not depend on a fresh JVM, nor share anything with the tests of the other forks:

- The home of the CLI (`CommandLineHelper.getHomeDir()`: the `.camel` directory, the user configuration and the
  plugins) is a static field. A test that needs a home calls `useTestHomeDir()` of `CamelCommandBaseTestSupport`,
  which gives it an empty temporary home and restores the previous one after the test. Never use a fixed directory
  such as `target` or `target/test-xxx`, as the other forks use it at the same time. A test that sets the home itself
  restores the previous one in `@AfterEach`.
- Each fork has a `user.home` of its own under `target` (see `pom.xml`), so the tests do not see the `~/.camel` of
  the developer. From an IDE, `user.home` is the real home: `UserConfigHelper` refuses to write the user
  configuration there.
- The forks share the working directory (the module directory). A test whose command uses it is annotated
  `@UsesWorkingDirectory`: `camel run` deletes `.camel-jbang`, `export`, `dependency` and `transform` write in it,
  `camel init` writes a file given without a directory there, and a local `camel-cli.properties` is read from it. A
  test that listens on a fixed port, such as `camel infra run` with the default port of a service, is annotated
  `@UsesFixedPort`. These tests run after the others, one class at a time, in a fork of their own (the `serial-test`
  execution). Prefer a temporary directory (`--dir`) and port 0, so a test does not need them.
- Restore what else a test changes in the JVM: system properties (`@SetSystemProperty` of junit-pioneer, or set and
  clear them), static fields, and stop the servers, threads and CamelContexts that it starts.
- A test that only fails now and then usually shares something with another test: run the module a few times with
  `-Dcamel.surefire.forkCount=8` to find out. `-Dtest=SomeTest` runs one class, whichever execution it belongs to.
