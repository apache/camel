# REST API

A REST API on port 8080, served by the HTTP server built into the Camel CLI: `GET /api/hello` answers the
greeting from `application.properties` and `GET /api/hello/{name}` answers a greeting with the name.

## What you will see

```text
$ curl localhost:8080/api/hello
Hello from Camel REST API!

$ curl localhost:8080/api/hello/World
Hello World from Camel REST API!
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

- `rest-api.camel.yaml` declares the API with the REST DSL: a `rest` with `path: /api` and two `get`
  operations, `/hello` and `/hello/{name}`. Each operation hands over to a `direct` route with `to`.
- The two `direct` routes set the body that becomes the HTTP response: a constant with the greeting from
  `application.properties`, and a simple expression in which the `{name}` path parameter arrives as the
  header `name`.
- The CLI starts the HTTP server because the file uses the REST DSL; `camel.server.port` in
  `application.properties` sets the port.

## Build it step by step

Ask your assistant, or type it yourself, one step at a time, and run after each:

1. A `rest` with `path: /api` and one `get` on `/hello` that goes to a direct route answering "Hello";
   run it and `curl localhost:8080/api/hello`.
2. Add the `/hello/{name}` operation and a second direct route that answers with `${header.name}`.
3. Move the greeting and the port to `application.properties`.

## Try changing

- Add `POST /api/hello` that answers with the request body: `curl -d 'Camel' localhost:8080/api/hello`.
- Set the header `Content-Type` to `application/json` and answer `{"greeting": "${body}"}`.
- Run with `--console` and open http://localhost:8080/q/dev to see the routes and their statistics.

## Integration testing

The example comes with a test in the [Citrus](https://citrusframework.org/) YAML DSL,
`test/rest-api.citrus.it.yaml`, which the Camel CLI runs:

```shell
camel test run test/rest-api.citrus.it.yaml
```

The test starts the route and calls both operations over HTTP.

## Help and contributions

If you hit any problem using Camel or have some feedback, then please
[let us know](https://camel.apache.org/community/support/).

We also love contributors, so
[get involved](https://camel.apache.org/community/contributing/) :-)

The Camel riders!
