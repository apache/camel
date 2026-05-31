## Circuit Breaker

This example shows how Camel JBang can use circuit breaker EIP.

### Install JBang

First install JBang according to https://www.jbang.dev

When JBang is installed then you should be able to run from a shell:

```sh
$ jbang --version
```

This will output the version of JBang.

To run this example you can either install Camel on JBang via:

```sh
$ jbang app install camel@apache/camel
```

Which allows to run Camel JBang with `camel` as shown below.

### How to run

You can run this example using:

```sh
$ camel run *
```

While the Camel integration is running, then from another terminal type:

```sh
$ camel get circuit-breaker
```

Which then output the state of the circuit breaker. You can run this command with `--watch` and see
how the state of the circuit breaker changes from closed to open due to many failures.

```sh
$ camel get circuit-breaker --watch
```

### Inspecting errors

Because the circuit breaker triggers exceptions, you can use `camel get error` to inspect
captured routing errors:

```sh
$ camel get error
```

This shows a summary table with PID, route, node, exchange ID, exception type and message.

To see full details of the last error (body, headers, variables, properties, exception and message history):

```sh
$ camel get error --last
```

You can also pick a specific error by its exchange ID:

```sh
$ camel get error --id=<exchangeId> --detail
```

### Help and contributions

If you hit any problem using Camel or have some feedback, then please
[let us know](https://camel.apache.org/community/support/).

We also love contributors, so
[get involved](https://camel.apache.org/community/contributing/) :-)

The Camel riders!
