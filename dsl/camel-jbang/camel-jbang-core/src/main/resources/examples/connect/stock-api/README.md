# Stock API

The shop's stock service: what the warehouse has on the shelf, per SKU. A REST API with two operations, served by
the HTTP server built into the Camel CLI, answering from a JSON file.

## What you will see

```text
$ curl localhost:8080/stock/CAMEL-MUG
{"sku":"CAMEL-MUG","qty":42}

$ curl -i localhost:8080/stock/CAMEL-SOCKS
HTTP/1.1 404 Not Found
{"error": "unknown sku CAMEL-SOCKS"}

$ curl localhost:8080/stock
[
  {"sku": "CAMEL-TSHIRT", "qty": 120},
  {"sku": "CAMEL-MUG", "qty": 42},
  {"sku": "CAMEL-CAP", "qty": 0}
]
```

## Install Camel CLI

Install [JBang](https://www.jbang.dev/download/) and the Camel CLI as described in the
[root README](../../README.md#install-the-camel-cli); `camel --version` confirms the install.

## Run it

```shell
camel run *
```

Then, in another terminal, call it with `curl` as above. Stop it with `ctrl` + `c`.

## How it works

- `rest` with `path: /stock` declares the API: a `get` with no path for the list, a `get` with `/{sku}` for one
  item. Each operation hands over to a `direct` route. The CLI starts the HTTP server on port 8080 because the
  file uses the REST DSL; `application.properties` sets the port.
- The list route sets the body to `resource:file:stock.json`, the file next to the route, and the content type.
- The item route loads the same file and picks the item with a `jsonpath` filter, `$[?(@.sku == '...')]`, in which
  the `{sku}` path parameter arrives as the header `sku`. The result is a list: empty means 404 with a small error
  body, otherwise the first element is marshalled back to JSON.
- `CamelHttpResponseCode` is the header that sets the status code; 200 is the default.

## Build it step by step

1. A `rest` with one `get` that answers `resource:file:stock.json`; run it and `curl localhost:8080/stock`.
2. Add the `/{sku}` operation and log `${header.sku}` to see the path parameter arrive.
3. Filter the file with `jsonpath` and return the first element; `curl` a known SKU.
4. Add the `choice` for the empty result and the 404.

## Try changing

- Add `POST /stock/{sku}` that logs the body: `curl -X POST -d '{"qty": 5}' localhost:8080/stock/CAMEL-CAP`.
- Change the port in `application.properties` and see the CLI pick it up.
- The `http-client` example calls this service from another route.

## Integration testing

The example comes with a test in the [Citrus](https://citrusframework.org/) YAML DSL,
`test/stock-api.citrus.it.yaml`, which the Camel CLI runs:

```shell
camel test run test/stock-api.citrus.it.yaml
```

The test starts the service and calls both operations over HTTP, checking the JSON and the 404.

## Help and contributions

If you hit any problem using Camel or have some feedback, then please
[let us know](https://camel.apache.org/community/support/).

We also love contributors, so
[get involved](https://camel.apache.org/community/contributing/) :-)

The Camel riders!
