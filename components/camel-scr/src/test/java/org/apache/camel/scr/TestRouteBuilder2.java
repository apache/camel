package org.apache.camel.scr;

import org.apache.camel.builder.RouteBuilder;

public class TestRouteBuilder2 extends RouteBuilder {
    @Override
    public void configure() throws Exception {
        from("direct://start2")
                .routeId("Second")
                .log("{{messageOk}}")
                .to("{{to}}");
    }
}
