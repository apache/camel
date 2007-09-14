package org.apache.camel.language.groovy.example

import org.apache.camel.language.groovy.GroovyRouteBuilder;

class GroovyRoutes extends GroovyRouteBuilder {

  void configure() {

    from("direct:a").filter {e ->
      e.in.headers.foo == "bar"
    }.to("mock:results")

  }
}