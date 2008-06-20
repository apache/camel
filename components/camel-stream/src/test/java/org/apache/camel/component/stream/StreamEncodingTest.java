package org.apache.camel.component.stream;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;

/**
 * Unit test for encoding option
 */
public class StreamEncodingTest extends ContextTestSupport {

    public void testStringContent() throws Exception {
        // include a UTF-8 char in the text \u0E08 is a Thai elephant
        String body = "Hello Thai Elephant \u0E08";

        template.sendBody("direct:in", body);
    }

    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:in").to("stream:out?encoding=UTF-8");
            }
        };
    }

}
