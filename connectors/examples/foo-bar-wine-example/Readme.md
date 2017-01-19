## Foo Bar and Wine Example

This is an example that uses the `foo`, `bar` and `wine` Camel connectors. These connectors
are used as if they are regular Camel components in Camel routes.

See the `FooBarWineRoute` class for more details.

### How to run

This example can be run from the command line using:

    mvn camel:run
    
### Apache Camel IDEA Plugin
    
You can use tooling such as the Apache Camel IDEA Plugin to offer code assistance while create the Camel route.

The tooling offers code completions such as listing the possible options you can use with the Camel connectors.
Notice how the tool presents only the pre-selected options of these connectors. For example the `foo` connector
which is based on the Camel `Timer` component only offers two options, where as if you are using `timer` instead
you will have many more options.

The following screenshot shows hows the `foo` connector only has two options to configure:

![Foo Connector in IDEA](img/foo-connector-options-idea.png?raw=true)

