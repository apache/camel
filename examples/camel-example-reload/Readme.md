Camel Example Reload
====================

This example runs a Camel Standalone application where the routes are defined in
Camel XML files located in `src/main/resources/camel/myroutes.xml`.

At runtime you can modify this file in the source code and then Camel will automatic reload
the route on-the-fly.

The reload only applies to Camel routes, eg the `<route>` elements inside `<camelContext>`.
For changes to Spring or OSGi Blueprint `<bean>`s or Java code, then alternative reload strategies
can be used. For example Spring Boot has a live reload tool, which you can try with the `camel-example-spring-boot-live-reload` example. OSGi Blueprint can be reloaded using Karaf container where you can run the `dev:watch *` command and
then rebuild your code with `mvn install` which triggers Karaf to redeploy the bundle when the SNAPSHOT jar is updated in the local Maven repository.

### How to try

You need to build the example first with

    mvn compile
    
Then you can run it from Maven
    
    mvn camel:run
        
### Reload changes
    
When the example is running then try to modify the XML file such as changing the message to be `Bye World`
    
      <transform>
        <constant>Bye World</constant>
      </transform>

You modify the source file directory in the `src/main/resources/camel/` directory

### Enabling live reload

Live reload is enabled in the `camel-maven-plugin` as shown below:

      <plugin>
        <groupId>org.apache.camel</groupId>
        <artifactId>camel-maven-plugin</artifactId>
        <version>${project.version}</version>
        <configuration>
          <!-- turn on reload when the XML file is updated in the source code -->
          <fileWatcherDirectory>src/main/resources/META-INF/spring</fileWatcherDirectory>
        </configuration>
      </plugin>
