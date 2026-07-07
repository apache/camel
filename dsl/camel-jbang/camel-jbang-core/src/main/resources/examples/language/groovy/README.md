## Groovy

This example shows how to use Groovy with extra dependencies in Camel CLI.

The route uses `EmailValidator` from [Apache Commons Validator](https://commons.apache.org/proper/commons-validator/)
to validate an email address and route the message accordingly using content-based routing.

The extra dependency is declared in `application.properties` using the
`camel.jbang.dependencies` property:

```properties
camel.jbang.dependencies=commons-validator:commons-validator:1.10.1
```

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

To see the invalid email branch, edit `groovy.camel.yaml` and change the `contactEmail` header in the `once` URI to an invalid value:

```yaml
uri: once:validate?header.contactEmail=not-a-valid-email
```

You can also declare dependencies as a modeline comment at the top of the YAML route file:

```yaml
#//DEPS commons-validator:commons-validator:1.10.1
```

Or pass the dependency on the command line:

```sh
$ camel run groovy.camel.yaml --dep=commons-validator:commons-validator:1.10.1
```

### Help and contributions

If you hit any problem using Camel or have some feedback, then please
[let us know](https://camel.apache.org/community/support/).

We also love contributors, so
[get involved](https://camel.apache.org/community/contributing/) :-)

The Camel riders!
