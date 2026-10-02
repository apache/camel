# File processing

The courier's billing system drops invoices into an inbox directory. Each invoice is checked, archived under a
month directory and moved to `done`; a bad one goes to `failed`; a file that is not an invoice is left alone.
The first route plays the courier: it copies the five files in `samples/` into `inbox/`.

## What you will see

```text
INFO ... file-processing.camel.yaml:47 : Invoice INV-2001 for ORD-1001: 49.4 EUR, archived as 2026-09/invoice-2001.json
INFO ... file-processing.camel.yaml:47 : Invoice INV-2002 for ORD-1002: 28.5 EUR, archived as 2026-09/invoice-2002.json
WARN ... file-processing.camel.yaml:11 : Invoice invoice-2003.json rejected: Validation failed for Predicate[bodyOgnl([amount]) > 0]. ...
ERROR ... DefaultErrorHandler : Failed delivery for (MessageId: ...). Exhausted after delivery attempt: 1 caught: ...PredicateValidationException: ...
WARN ... GenericFileOnCompletion : Rollback file strategy: ...GenericFileRenameProcessStrategy
INFO ... file-processing.camel.yaml:47 : Invoice INV-2004 for ORD-1003: 53.45 EUR, archived as 2026-09/invoice-2004.json
```

## Install Camel CLI

Install [JBang](https://www.jbang.dev/download/) and the Camel CLI as described in the
[root README](../../README.md#install-the-camel-cli); `camel --version` confirms the install.

## Run it

```shell
camel run *
```

Afterwards the directories tell the story: `inbox/done/` holds the three good invoices, `inbox/failed/` the
rejected one, `archive/<year-month>/` copies of the good ones, and `inbox/note-2005.txt` is still where it was.
Delete `inbox/` and `archive/` to run it again.

## How it works

- The `courier` route is a `file` consumer on `samples/` with `noop: true` writing to `file:inbox`, a copy.
- The `invoices` route is the lesson: a `file` consumer on `inbox/` with `include: .*\.json`, so the driver's
  note is never picked up; `move: done` for files that went through, `moveFailed: failed` for files whose
  exchange failed. Both are directories relative to the inbox.
- `validate` throws a `PredicateValidationException` when the amount is not positive; the `onException` logs a
  warning without a stack trace and lets the exception through, which is what makes the consumer use `moveFailed`.
  Had it set `handled: true`, the file would have been moved to `done` as if it had succeeded.
- The archive copy uses a `fileName` expression with `${date:now:yyyy-MM}` for the directory and `${file:name}`
  for the name; the file component creates directories as needed.

## Build it step by step

1. A `file` route on `inbox` that logs `${file:name}`; drop a file in and see it logged, then note the
   `.camel` directory the consumer moved it to by default.
2. Add `move: done` and `include: .*\.json`, drop the note in and see it ignored.
3. Add the `courier` route so the samples arrive by themselves, and `unmarshal` and log the invoice.
4. Add `validate` and `moveFailed: failed`, then the `onException` to tidy the log.

## Try changing

- Add `idempotent: true` with `idempotentKey: ${file:name}` and drop the same invoice twice.
- Replace `move: done` with `delete: true`.
- Point `archive` at a `sftp:` endpoint; the `ftp` example on the next rung shows how.

## Integration testing

The example comes with a test in the [Citrus](https://citrusframework.org/) YAML DSL,
`test/file-processing.citrus.it.yaml`, which the Camel CLI runs:

```shell
camel test run test/file-processing.citrus.it.yaml
```

The test starts the routes and verifies the archived and rejected lines.

## Help and contributions

If you hit any problem using Camel or have some feedback, then please
[let us know](https://camel.apache.org/community/support/).

We also love contributors, so
[get involved](https://camel.apache.org/community/contributing/) :-)

The Camel riders!
