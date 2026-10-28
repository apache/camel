# Timer Log

The hello of Camel in one file: a timer fires every second and a log line prints the greeting from
`application.properties`.

## What you will see

```text
INFO ... timer-log.camel.yaml:13 : Hello Camel!
INFO ... timer-log.camel.yaml:13 : Hello Camel!
INFO ... timer-log.camel.yaml:13 : Hello Camel!
```

## Install Camel CLI

Install [JBang](https://www.jbang.dev/download/) and the Camel CLI as described in the
[root README](../../README.md#install-the-camel-cli); `camel --version` confirms the install.

## Run it

```shell
camel run *
```

Stop it with `ctrl` + `c`, or from another terminal with `camel stop timer-log`.

## How it works

- `timer-log.camel.yaml` is the route: `from` a `timer` that fires every `timer.period` milliseconds, a
  `setBody` puts the greeting in the message, and a `log` prints the body.
- `application.properties` holds the period and the greeting. `{{timer.period}}` and `{{greeting.message}}`
  in the route are property placeholders, resolved when the route starts.
- The Camel CLI reads every file in the directory: the route because it ends in `.camel.yaml`, the properties
  because of their name.

## Build it step by step

Ask your assistant, or type it yourself, one step at a time, and run after each:

1. A route from a timer that logs "Hello Camel!" every second.
2. Move the greeting to `application.properties` and set the body from the property.
3. Move the period to a property too.

## Try changing

- `timer.period=5000` for a greeting every five seconds; the route file does not change.
- Add `repeatCount: 3` to the timer parameters and the timer stops after three greetings.
- Log `${date:now:HH:mm:ss} ${body}` to see when each line was written.

## Integration testing

The example comes with a test in the [Citrus](https://citrusframework.org/) YAML DSL,
`test/timer-log.citrus.it.yaml`, which the Camel CLI runs:

```shell
camel test run test/timer-log.citrus.it.yaml
```

The test starts the route and verifies the logged greeting.

## Help and contributions

If you hit any problem using Camel or have some feedback, then please
[let us know](https://camel.apache.org/community/support/).

We also love contributors, so
[get involved](https://camel.apache.org/community/contributing/) :-)

The Camel riders!
