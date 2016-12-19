Camel Example Reload
====================

This example runs a standalone Camel Spring application where the routes are defined in
the Spring XML located in `src/main/resources/META-INF/spring/camel-context.xml`.

At runtime you can modify this file in the source code and then Camel will automatic reload
the route on-the-fly.

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
