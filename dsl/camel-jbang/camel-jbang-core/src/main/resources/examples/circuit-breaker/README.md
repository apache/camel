# Circuit Breaker

This example shows how Camel JBang can use circuit breaker EIP.

## How to run

You can run this example using:

```sh
camel run *
```

While the Camel integration is running, then from another terminal type:

```sh
camel get circuit-breaker
```

Which then output the state of the circuit breaker. You can run this command with `--watch` and see
how the state of the circuit breaker changes from closed to open due to many failures.

```sh
camel get circuit-breaker --watch
```
