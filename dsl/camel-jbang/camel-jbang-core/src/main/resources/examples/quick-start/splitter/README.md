# Splitter

A timer creates a comma-separated batch of items, the splitter turns it into one message per item, and each item
is logged on its own line.

## What you will see

```text
INFO ... splitter.camel.yaml:14 : Received batch: Laptop,Phone,Tablet,Monitor,Keyboard
INFO ... splitter.camel.yaml:21 : Processing item 0: Laptop
INFO ... splitter.camel.yaml:21 : Processing item 1: Phone
INFO ... splitter.camel.yaml:21 : Processing item 2: Tablet
INFO ... splitter.camel.yaml:21 : Processing item 3: Monitor
INFO ... splitter.camel.yaml:21 : Processing item 4: Keyboard
```

The batch is sent three times, five seconds apart, and then the timer stops.

## Install Camel CLI

Install [JBang](https://www.jbang.dev/download/) and the Camel CLI as described in the
[root README](../../README.md#install-the-camel-cli); `camel --version` confirms the install.

## Run it

```shell
camel run *
```

Stop it with `ctrl` + `c`, or from another terminal with `camel stop splitter`.

## How it works

- `splitter.camel.yaml` is the route: a `timer` with `repeatCount: 3` fires three times, a `setBody` puts the
  batch in the message as one string, and a `log` prints it.
- `split` with a `tokenize` expression on `,` turns the one message into five, one per item. The steps under
  the `split` run once for each of them: here a `log` with the item.
- `CamelSplitIndex` is a header the splitter sets on each part, counting from 0; `CamelSplitSize` is the
  total and `CamelSplitComplete` is true on the last part.
- After the last part the route continues after the `split` with the original batch as the body; there is
  nothing after it here.

## Build it step by step

Ask your assistant, or type it yourself, one step at a time, and run after each:

1. A route from a timer that sets the body to "Laptop,Phone,Tablet" and logs it.
2. Split the body on the comma and log each part.
3. Add the split index to the log line, and `repeatCount: 3` to the timer.

## Try changing

- Log `${header.CamelSplitIndex} of ${header.CamelSplitSize}` on each part.
- Add a `log` after the `split` and see the original batch come back as the body.
- Split a JSON array instead: set the body to `["Laptop","Phone"]` and use `jsonpath: {expression: "$[*]"}`
  as the split expression.

## Integration testing

The example comes with a test in the [Citrus](https://citrusframework.org/) YAML DSL,
`test/splitter.citrus.it.yaml`, which the Camel CLI runs:

```shell
camel test run test/splitter.citrus.it.yaml
```

The test starts the route and verifies the batch and the last item are logged.

## Help and contributions

If you hit any problem using Camel or have some feedback, then please
[let us know](https://camel.apache.org/community/support/).

We also love contributors, so
[get involved](https://camel.apache.org/community/contributing/) :-)

The Camel riders!
