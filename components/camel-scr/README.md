camel-scr
=========

Camel SCR support (OSGi Declarative Services)

Running Camel in an SCR bundle is a great alternative for the more common methods (Spring DM and Blueprint). Using Camel SCR support your bundle can remain completely in Java world; there is no need to create or modify any XML or properties files. This offers us full control over everything and means that your IDE of choice knows exactly what is going on in your project.

### Usage

*AbstractCamelRunner* ties CamelContext's lifecycle to Service Component's lifecycle and handles configuration with Camel's PropertiesComponent. All you have to do is extend your a Java class from *AbstractCamelRunner* and add the following org.apache.felix.scr.annotations on class level:

```
@Component
@References({
        @Reference(name = "camelComponent",referenceInterface = ComponentResolver.class,
                cardinality = ReferenceCardinality.MANDATORY_MULTIPLE, policy = ReferencePolicy.DYNAMIC,
                policyOption = ReferencePolicyOption.GREEDY, bind = "gotCamelComponent", unbind = "lostCamelComponent")
})
```

Then implement *getRouteBuilders()* which should return the Camel routes you want to run. And finally provide the default configuration with:

```
@Properties({
  @Property(name = "camelContextId", value = "my-test"),
  @Property(name = "active", value = "true"),
  @Property(name = "...", value = "..."),
  ...
})
```

That's all. And if you use camel-archetype-scr all this is already taken care of.

Properties *camelContextId* and *active* control the CamelContext's name (defaults to "camel-runner-default") and whether it will be started or not (defaults to "false"), respectively. In addition to these you can add and use as many properties as you like. PropertiesComponent handles recursive properties and prefixing with fallback with no problem (see camel-archetype-scr generated example for more).

*AbstractCamelRunner* will make these properties available to your RouteBuilders through Camel's PropertiesComponent AND it will also inject these values into your Service Component class' and RouteBuilder's fields when their names match. The fields can be declared with any visibility level, and many types are supported (String, int, boolean, URL, ...).

### Lifecycle

AbstractCamelRunner lifecycle in SCR:

1. When component's configuration policy and mandatory references are satisfied SCR calls activate(). This creates and sets up a CamelContext through the following call chain: *activate()* -> *prepare()* -> *createCamelContext()* -> *setupPropertiesComponent()* -> *configure()* -> *setupCamelContext()*. Finally, the context is scheduled to start after *AbstractCamelRunner.START_DELAY* with *runWithDelay()*.
2. When Camel components (actually ComponentResolvers) are registered SCR calls *gotCamelComponent()* which reschedules the CamelContext start to happen after *AbstractCamelRunner.START_DELAY*. This causes the CamelContext to wait until all Camel components are loaded or there is a sufficient gap between them. The same logic will reschedule a failed-to-start CamelContext whenever we add more (hopefully the missing ones) Camel components.
3. When Camel components are unregistered SCR calls *lostCamelComponent()*. This is a no-op.
4. When one of the requirements that caused the *activate()* to be called is lost SCR will call *deactivate()*. This will shutdown the CamelContext.

In unit tests we don't generally use *activate()* -> *deactivate()*, but *prepare()* -> *run()* -> *stop()* for a more fine-grained control. Also, this allows us to avoid possible SCR specific operations in tests.

### Examples

You can generate an example with camel-archetype-scr.
