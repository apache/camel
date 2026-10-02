# Nightly report

The shop's inventory report on a schedule. A cron expression triggers the route instead of a timer: every
ten seconds in the demo, nightly at 02:00 with a one-line change in `application.properties`.

## What you will see

```text
INFO ... nightly-report.camel.yaml:15 : Inventory report 2026-09-18 15:04:10: 120 T-shirts and 45 mugs in stock
INFO ... nightly-report.camel.yaml:15 : Inventory report 2026-09-18 15:04:20: 120 T-shirts and 45 mugs in stock
```

## Install Camel CLI

Install [JBang](https://www.jbang.dev/download/) and the Camel CLI as described in the
[root README](../../README.md#install-the-camel-cli); `camel --version` confirms the install.

## Run it

```shell
camel run *
```

## How it works

- The `cron` component fires on the schedule `report.schedule`, a cron expression with seconds first.
- `setBody` builds the report line with the simple language: the current time with `${date:now:...}`
  and the stock counts from properties.
- The Camel CLI adds the scheduler (camel-quartz) the cron component needs on its own.

## Build it step by step

1. Start from a timer route that logs "Inventory report" every ten seconds.
2. Replace the timer with `cron` and the schedule `0/10 * * * * ?`; the log keeps coming every ten seconds.
3. Add the current time to the message with `${date:now:yyyy-MM-dd HH:mm:ss}`.
4. Move the schedule and the stock counts to `application.properties` and use them as `{{...}}`.
5. Change the schedule to `0 0 2 * * ?` and read it back: at two in the morning, every night.

## Try changing

- `0 * * * * ?` runs at the top of every minute.
- Log the report at `WARN` level when a count drops below ten: the `content-based-router` example shows
  the `choice` you need.

## Integration testing

The example comes with a test in the [Citrus](https://citrusframework.org/) YAML DSL,
`test/nightly-report.citrus.it.yaml`, which the Camel CLI runs:

```shell
camel test run test/nightly-report.citrus.it.yaml
```

The test starts the route and verifies the logged line.

## Help and contributions

If you hit any problem using Camel or have some feedback, then please
[let us know](https://camel.apache.org/community/support/).

We also love contributors, so
[get involved](https://camel.apache.org/community/contributing/) :-)

The Camel riders!
