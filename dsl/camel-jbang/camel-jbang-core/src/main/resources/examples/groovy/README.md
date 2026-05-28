# Groovy

This example shows how to use Groovy with extra dependencies in Camel JBang.

The route uses `EmailValidator` from [Apache Commons Validator](https://commons.apache.org/proper/commons-validator/)
to validate an email address and route the message accordingly using content-based routing.

The extra dependency is declared in `application.properties` using the
`camel.jbang.dependencies` property:

```properties
camel.jbang.dependencies=commons-validator:commons-validator:1.10.1
```

## How to run

You can run this example using:

```sh
camel run *
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
camel run groovy.camel.yaml --dep=commons-validator:commons-validator:1.10.1
```
