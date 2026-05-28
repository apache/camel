## Message Size

This example demonstrates Camel's message size tracking feature, which captures
body and header sizes per endpoint for both incoming (IN) and outgoing (OUT) directions.

Three timer-driven producers simulate messages of different sizes (small, medium, large)
using the `Content-Length` header and send them to separate SEDA endpoints.
The size statistics (min, max, mean) are tracked per endpoint and can be viewed via the CLI.

### How to run

```sh
$ camel run *
```

### Viewing message size statistics

While the integration is running, open another terminal and use the `camel` CLI
to view endpoint statistics including message sizes:

```sh
$ camel get endpoint
```

To see detailed min/max statistics:

```sh
$ camel get endpoint --verbose
```

To sort endpoints by body size (largest first):

```sh
$ camel get endpoint --sort=-size
```

### How it works

Message size tracking is automatically enabled when running with `camel run`
which uses the dev profile. This sets:

- `camel.main.messageSizeEnabled = true`
- `camel.main.jmxManagementStatisticsLevel = Extended`

Sizes are tracked per endpoint in the runtime endpoint registry. For incoming messages,
the body and headers sizes are also available as exchange properties
(`CamelMessageBodySize` and `CamelMessageHeadersSize`) during routing.

### Help and contributions

If you hit any problem using Camel or have some feedback, then please
[let us know](https://camel.apache.org/community/support/).

We also love contributors, so
[get involved](https://camel.apache.org/community/contributing/) :-)

The Camel riders!
