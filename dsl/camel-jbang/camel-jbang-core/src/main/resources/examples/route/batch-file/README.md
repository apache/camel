# Batch file

The payment provider sends the shop a settlement file every day, one CSV line per payment. The route reads each file
line by line with a streaming split, puts the lines it cannot read aside, and logs a summary when a file is done. The
first route plays the payment provider: it copies the two files in `samples/` into `inbox/`.

## What you will see

```text
INFO ... batch-file.camel.yaml:29 : Settlement settlement-2026-11-09.csv received: 183371 bytes
INFO ... batch-file.camel.yaml:64 : Large payment PAY-000198 for ORD-1198: 659.64 EUR
INFO ... batch-file.camel.yaml:64 : Large payment PAY-000434 for ORD-1434: 1633.36 EUR
...
INFO ... batch-file.camel.yaml:88 : Settlement settlement-2026-11-09.csv done: 5000 lines
INFO ... batch-file.camel.yaml:29 : Settlement settlement-2026-11-10.csv received: 184381 bytes
...
INFO ... batch-file.camel.yaml:70 : Rejected from settlement-2026-11-10.csv: PAY-006117,ORD-7117,C-319,N/A,EUR
...
INFO ... batch-file.camel.yaml:70 : Rejected from settlement-2026-11-10.csv: PAY-008840,ORD-9840,C-207,1O.50,EUR
...
INFO ... batch-file.camel.yaml:64 : Large payment PAY-009672 for ORD-10672: 891.29 EUR
INFO ... batch-file.camel.yaml:88 : Settlement settlement-2026-11-10.csv done: 5000 lines
```

## Install Camel CLI

Install [JBang](https://www.jbang.dev/download/) and the Camel CLI as described in the
[root README](../../README.md#install-the-camel-cli); `camel --version` confirms the install.

## Run it

```shell
camel run *
```

A file that is done moves to `inbox/done`, and the lines that could not be read are in `rejects/`. Stop the example
with `ctrl` + `c`. Delete `inbox/` and `rejects/` before you run it again: the rejects are appended to their file.

## How it works

- `samples/` holds two settlement files of the payment provider, 5000 payments each, with a header line. In
  `settlement-2026-11-10.csv` two amounts are not a number: `N/A`, and `1O.50` with the letter O.
- The `provider` route is a `file` consumer on `samples/` with `noop: true` writing to `file:inbox`, a copy.
- The `settlement` route takes the CSV files in `inbox/` in name order (`sortBy`) and moves each to `inbox/done` when
  it is done, or to `inbox/failed` when it fails (`move` and `moveFailed` are relative to `inbox`).
- `split` with the `tokenize` language cuts the file into lines, and `skipFirst` leaves out the header line. With
  `streaming: true` the split reads one line at a time instead of all lines first, so the size of the file does not
  matter.
- Each line is kept in the variable `line`. The CSV data format turns it into a map with the column names of
  `header` (`useMaps`), in a list with one record, so the first record becomes the body.
- The amount is converted to a `BigDecimal` in the variable `amount` (`resultType`), so the filter compares numbers.
  As text, `"1633.36" >= "500"` is false.
- `doTry` and `doCatch`: a line that fails, here because its amount is not a number, is logged and appended to
  `rejects/<file name>` (`fileExist: Append`, `appendChars` for the line break). The split goes on with the next line,
  and the file still counts as done.
- On the last line the split sets the exchange property `CamelSplitComplete`, and `CamelSplitSize` holds the number of
  lines. With streaming the size is only known at the end, so the summary is logged there.
- `application.properties` declares `inbox` and `rejects` as data (`camel.jbang.dataDir`), so `camel run --dev` does
  not reload when the route moves or writes a file.

## Build it step by step

Ask your assistant, or type it yourself, one step at a time, and run after each:

1. A `provider` route that copies the files in `samples/` to `inbox/`, and a file route on `inbox` for the CSV files,
   in name order, that moves each file to `inbox/done` when it is done and logs "Settlement ... received" with the
   size of the file.
2. Split the file into lines with streaming, skip the header line, read each line with the CSV data format into a map
   (paymentId, orderId, customer, amount, currency), and log each payment of 500 EUR or more, and nothing for
   the other payments.
3. Write each line that cannot be read to `rejects/<file name>` and go on with the next line, so the file still ends in
   `inbox/done`.
4. When the whole file is read, log a summary with the number of lines.

## Try changing

- Remove `streaming: true`: the result is the same here, but the split reads all 5000 lines into memory first. With a
  file of millions of lines, that is the difference between a batch that runs and an `OutOfMemoryError`.
- Remove the `doTry`: the split still goes on after a bad line (`stopOnException` is false by default), but at the end
  the file counts as failed and moves to `inbox/failed`.
- Set `stopOnException: true` on the split, without the `doTry`: the file stops at the first bad line.
- Add `parallelProcessing: true` to the split: the lines are processed in parallel, so the large payments may be logged
  out of order.

## Integration testing

The example comes with a test in the [Citrus](https://citrusframework.org/) YAML DSL,
`test/batch-file.citrus.it.yaml`, which the Camel CLI runs:

```shell
camel test run test/batch-file.citrus.it.yaml
```

The test runs the route and verifies a large payment, a rejected line and the summary of a file.

## Help and contributions

If you hit any problem using Camel or have some feedback, then please
[let us know](https://camel.apache.org/community/support/).

We also love contributors, so
[get involved](https://camel.apache.org/community/contributing/) :-)

The Camel riders!
