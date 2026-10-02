# Routes

The first step from YAML into your own code: a timer route in YAML calls a Java bean, `Greeter`, that builds
the message, and logs what the bean returned.

## What you will see

```text
INFO ... routes.camel.yaml:16 : Hello, Camel!
INFO ... routes.camel.yaml:16 : Hello, Camel!
```

## Install Camel CLI

Install [JBang](https://www.jbang.dev/download/) and the Camel CLI as described in the
[root README](../../README.md#install-the-camel-cli); `camel --version` confirms the install.

## Run it

```shell
camel run *
```

Stop it with `ctrl` + `c`, or from another terminal with `camel stop routes`.

## How it works

- `routes.camel.yaml` is the route: a `timer` fires every second, a `setBody` puts the name `Camel` in the
  message, a `bean` step calls the `greet` method of the bean `greeter` with the body, and a `log` prints
  what the method returned, which is now the body.
- `Greeter.java` is a plain Java class next to the route, with no Camel API in it: a `greeting` property and a
  `greet(String name)` method. The CLI compiles it when the route starts.
- `beans.yaml` declares the class as the bean `greeter` and sets its `greeting` from a property.
- `application.properties` holds the greeting and the timer period.

## Build it step by step

Ask your assistant, or type it yourself, one step at a time, and run after each:

1. A route from a timer that sets the body to "Camel" and logs it.
2. A Java class `Greeter` in the package `camel.example` with a method `greet(String name)` that returns
   "Hello, " and the name, declared as the bean `greeter` in `beans.yaml`; call it from the route with a
   `bean` step before the log.
3. Give the class a `greeting` property, set it in `beans.yaml`, and move its value to
   `application.properties`.

## Try changing

- `greeter.greeting=Hej` in `application.properties`; the Java class and the route do not change.
- Add a second method `shout(String name)` that returns the greeting in upper case and switch the `method` in
  the route.
- Leave out `method: greet` and see that Camel finds the single public method by itself.

## Integration testing

The example comes with a test in the [Citrus](https://citrusframework.org/) YAML DSL,
`test/routes.citrus.it.yaml`, which the Camel CLI runs:

```shell
camel test run test/routes.citrus.it.yaml
```

The test starts the route, with the bean and the Java class, and verifies the logged greeting.

## Help and contributions

If you hit any problem using Camel or have some feedback, then please
[let us know](https://camel.apache.org/community/support/).

We also love contributors, so
[get involved](https://camel.apache.org/community/contributing/) :-)

The Camel riders!
