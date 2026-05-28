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

## Inspecting errors

Because the circuit breaker triggers exceptions, you can use `camel get error` to inspect
captured routing errors:

```sh
camel get error
```

This shows a summary table with PID, route, node, exchange ID, exception type and message.

To see full details of the last error (body, headers, variables, properties, exception and message history):

```sh
camel get error --last
```

You can also pick a specific error by its exchange ID:

```sh
camel get error --id=<exchangeId> --detail
```
