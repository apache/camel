# Routes

This example shows how routes are defined in Yaml.

## How to run

You can run this example using:

```sh
camel run *
```

Camel will start a route that periodically provides a greeting message.

## Live reload

You can run the example in dev mode which allows you to edit the example,
and hot-reload when the file is saved.

```sh
camel run * --dev
```

## Run directly from GitHub

The example can also be run directly by referring to the GitHub URL as shown:

```sh
camel run https://github.com/apache/camel-jbang-examples/tree/main/routes
```

## Developer Web Console

You can enable the developer console via `--console` flag as show:

```sh
camel run * --console
```

Then you can browse: http://localhost:8080/q/dev to introspect the running Camel Application.
Under "beans" Camel should display bean `greeter`.
