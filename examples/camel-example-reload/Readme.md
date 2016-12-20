Camel Example Reload
====================

This example runs a standalone Camel Spring application where the routes are defined in
the Spring XML located in `src/main/resources/META-INF/spring/camel-context.xml`.

At runtime you can modify this file in the source code and then Camel will automatic reload
the route on-the-fly.

The reload only applies to Camel routes, eg the `<route>` elements inside `<camelContext>`.
For changes to Spring or OSGi Blueprint `<bean>`s or Java code, then alternative reload strategies
can be used. For example Spring Boot has a live reload tool, which you can try with the `camel-example-spring-boot-live-reload` example. OSGi Blueprint can be reloaded using Karaf container where you can run the `dev:watch *` command and
then rebuild your code with `mvn install` which triggers Karaf to redeploy the bundle when the SNAPSHOT jar is updated
in the local Maven repository.

### How to try

You need to build the example first with

    mvn compile
    
Then you can run it from Maven
    
    mvn camel:run
    
Or you can run it from your Java IDE editor by running the following Main class
    
    org.apache.camel.example.reload.CamelReloadMain
    
### Reload changes
    
When the example is running then try to modify the XML file such as changing the message to be `Bye World`
    
      <transform>
        <constant>Bye World</constant>
      </transform>

You modify the source file directory in the `src/main/resources/META-INF/spring/` directory
