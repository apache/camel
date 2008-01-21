package org.apache.camel.component.http;

import org.apache.camel.builder.RouteBuilder;

public class HttpGetWithHeadersTest extends HttpGetTest {

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {
                from("direct:start")
                    .setHeader("Content-Length", constant(0))
                    .setHeader("Accept-Language", constant("pl"))
                    .to("http://www.google.com/search")
                    .to("mock:results");
            }
        };
    }

    @Override
    protected void setUp() throws Exception {
        // "Szukaj" is "Search" in polish language
        expectedText = "Szukaj";
        super.setUp();
    }

}
