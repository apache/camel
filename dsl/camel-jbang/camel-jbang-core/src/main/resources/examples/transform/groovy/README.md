# Groovy

Two orders come in, one with a valid customer email and one with a bad one. A Groovy expression checks the address
with Apache Commons Validator, a third-party library the Camel CLI downloads because `application.properties`
declares it.

## What you will see

```text
INFO ... groovy.camel.yaml:28 : Order ORD-1001 accepted: anna@example.com is a valid address
INFO ... groovy.camel.yaml:32 : Order ORD-1002 rejected: not-an-address is not an email address
```

## Install Camel CLI

Install [JBang](https://www.jbang.dev/download/) and the Camel CLI as described in the
[root README](../../README.md#install-the-camel-cli); `camel --version` confirms the install.

## Run it

```shell
camel run *
```

## How it works

- The timer fires twice with `includeMetadata: true`, so `CamelTimerCounter` tells the first order from the
  second; a simple expression picks `order.json` or `order-bad-email.json` from it.
- `unmarshal` with `json` parses the order, so the Groovy expression reads `body.email` on a map.
- `choice` with a `groovy` expression: `EmailValidator.getInstance().isValid(body.email)` from Commons Validator.
- `camel.jbang.dependencies=commons-validator:commons-validator:1.10.1` in `application.properties` is how a
  route declares a library that is not a Camel component; the Camel CLI downloads it.

## Build it step by step

1. A one-shot timer route that sets the body from `order.json`, unmarshals it, and logs `${body[email]}`.
2. Add a `choice` with a `groovy` expression `body.email.contains('@')` and log accepted or rejected.
3. Replace the check with Commons Validator's `EmailValidator` and declare the dependency in
   `application.properties`.
4. Let the timer fire twice with `includeMetadata: true` and pick the second file for the second order.

## Try changing

- Validate the country code too: `body.country in ['DK', 'SE', 'NO']`.
- Put the check in a script file, `validate-order.groovy`, and load it with `resource:file:`, as the
  `data-mapping` example does.

## Integration testing

The example comes with a test in the [Citrus](https://citrusframework.org/) YAML DSL,
`test/groovy.citrus.it.yaml`, which the Camel CLI runs:

```shell
camel test run test/groovy.citrus.it.yaml
```

The test starts the route and verifies that one order is accepted and one rejected.

## Help and contributions

If you hit any problem using Camel or have some feedback, then please
[let us know](https://camel.apache.org/community/support/).

We also love contributors, so
[get involved](https://camel.apache.org/community/contributing/) :-)

The Camel riders!
