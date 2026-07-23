## Routes

This example shows how routes are defined in Yaml.

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

Which allows to run Camel CLI with `camel` as shown below.

### How to run

You can run this example using:

```sh
$ camel run *
```

Camel will start a route that periodically provides a greeting message.

### Live reload

You can run the example in dev mode which allows you to edit the example,
and hot-reload when the file is saved.

```sh
$ camel run * --dev
```

### Run directly from GitHub

The example can also be run directly by referring to the GitHub URL as shown:

```sh
$ camel run https://github.com/apache/camel-jbang-examples/tree/main/routes
```

### Developer Web Console

You can enable the developer console via `--console` flag as show:

```sh
$ camel run * --console
```

Then you can browse: http://localhost:8080/q/dev to introspect the running Camel Application.
Under "beans" Camel should display bean `greeter`.


### Help and contributions

If you hit any problem using Camel or have some feedback, then please
[let us know](https://camel.apache.org/community/support/).

We also love contributors, so
[get involved](https://camel.apache.org/community/contributing/) :-)

The Camel riders!
