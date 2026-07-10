# Task 2 Report: TUI-Safe CLI Command Executor

## What I implemented

- Completed `AiCliCommandExecutor` with immutable CLI requests for `camel run`, `camel infra`, and `camel cmd send`.
- Mapped `/send` literal bodies to `--body=<text>` and `@file` bodies to `--body=file:<path>` without expanding files.
- Added quote-aware raw-tail tokenization using the already available JLine parser.
- Added a package-private invoker seam, daemon asynchronous execution, captured `Printer` output, exit-code reporting, and bounded cancellation.
- The production invoker captures and restores the main printer and restores the selected process after a targeted `cmd send` command.

## What I tested and results

`mvn -pl dsl/camel-jbang/camel-jbang-plugin-tui -Dtest=AiCliCommandExecutorTest test`

Result: `BUILD SUCCESS`, 8 tests run, 0 failures, 0 errors, 0 skipped.

## TDD Evidence

### RED

1. Ran the required command in the sandbox. Maven was blocked before compilation because the Develocity extension could not write under `~/.m2/.develocity`.
2. Reran the same command with escalation. It reached `testCompile` and failed with the expected missing API errors for `Request.run`, `Request.infra`, `Request.send`, `argv`, the invoker constructor, `executeAsync`, and `cancel`.

### GREEN

Ran the required command after implementation. The first GREEN attempt exposed that this repository's picocli 4.7.7 does not provide `CommandLine.translateCommandline(String)`. Replaced that unavailable API with JLine's existing quote-aware parser. A second run isolated quote delimiters following `=`. Normalized those delimiters, then reran the command successfully: 8 tests run with no failures, errors, or skips.

## Files changed

- `dsl/camel-jbang/camel-jbang-plugin-tui/src/main/java/org/apache/camel/dsl/jbang/core/commands/tui/AiCliCommandExecutor.java`
- `dsl/camel-jbang/camel-jbang-plugin-tui/src/test/java/org/apache/camel/dsl/jbang/core/commands/tui/AiCliCommandExecutorTest.java`
- `.superpowers/sdd/task-2-report.md`

## Self-review findings

- `git diff --check` reported no whitespace errors.
- No dependencies were added and no generated files changed.
- The focused test covers every required request mapping, output capture, nonzero exits, quoted values, and interruption without `Thread.sleep()`.
- The implementation does not call `CamelJBangMain.quit()`.

## Concerns

- The Task 1 registry remains on its intentionally minimal compatibility constructor because this task's write scope excludes registry wiring. A later task must route registry command arguments through the `Request` factories before the TUI can invoke these mapped commands.

## Review Fixes

- Production command execution now uses a guarded `CamelJBangMain` that records `quit(int)` as an exit code, so the TUI cannot reach `System.exit`. The previous static command line is restored with reflection after each execution.
- Cancellation now records a per-command cancellation request independently of the worker interrupt status. This preserves `Result.interrupted()` when an invoker catches `InterruptedException` and clears the flag.
- Raw-tail parsing only removes matching quote delimiters around an option value after `=`. Quotes within the parsed value are retained.
- Added coverage for quoted inner values, an invoker that consumes an interruption, and a cancellation timeout using the package-private timeout constructor. The production timeout remains 30 seconds.

## Review Verification

`mvn -pl dsl/camel-jbang/camel-jbang-plugin-tui -Dtest=AiCliCommandExecutorTest test`

Result: `BUILD SUCCESS`, 11 tests run, 0 failures, 0 errors, 0 skipped.

## Remaining Review Fixes

- `AiCliCommandExecutor` now keeps one active command object until its worker exits. A second `executeAsync` returns a failed future while a command is running, including after a cancellation timeout has completed the first result.
- The production invoker now follows the current-main printer contract: it gets the main from `CamelJBangMain.getCommandLine()`, saves and restores `main.getOut()`, captures output through `main.setOut(...)`, sets the selected process only for `cmd send <process>`, and executes the existing command line directly.
- Direct command-line execution deliberately bypasses `CamelJBangMain.execute(...)`, the only outer command path that invokes `quit()`. The focused production-invoker test uses a non-exiting main and verifies both printer restoration and that `quit()` was not called.
- Picocli 4.7.7 does not expose `CommandLine.translateCommandline(String)`. JLine's direct parser output retains quote delimiters after `=`, so it could not meet the dequoted-word contract. Replaced the fragile quote post-processing with a small shell-word splitter that removes syntactic quote delimiters while preserving literal quotes inside values.
- Added coverage for literal quote data and for rejecting a second command after cancellation times out while the original worker remains alive.

## Remaining Review Verification

`mvn -pl dsl/camel-jbang/camel-jbang-plugin-tui -Dtest=AiCliCommandExecutorTest test`

The sandbox run stopped before compilation because Develocity could not write under `~/.m2/.develocity`. The escalated rerun cleared that permission blocker but still stopped before compilation because this worktree is missing the required root module `camel-dependencies/pom.xml`. The new tests were not executed in this checkout.
