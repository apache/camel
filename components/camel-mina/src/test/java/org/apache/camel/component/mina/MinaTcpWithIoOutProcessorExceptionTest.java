package org.apache.camel.component.mina;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Processor;
import org.apache.camel.Exchange;
import org.apache.camel.CamelContext;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.builder.RouteBuilder;

/**
 * To unit test CAMEL-364.
 */
public class MinaTcpWithIoOutProcessorExceptionTest extends ContextTestSupport {

    protected CamelContext container = new DefaultCamelContext();

    private static final int PORT = 6334;
    // use parameter sync=true to force InOut pattern of the MinaExchange
    protected String uri = "mina:tcp://localhost:" + PORT + "?textline=true&sync=true";

    public void testExceptionThrownInProcessor() {
        String body = "Hello World";
        String out = (String) template.requestBody(uri, body);
        assertNull("Should not have sent data back", out);
    }

    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from(uri).process(new Processor() {
                    public void process(Exchange e) {
                        assertEquals("Hello World", e.getIn().getBody(String.class));
                        // simulate a problem processing the input to see if we can handle it properly
                        throw new IllegalArgumentException("Forced exception");
                    }
                });
            }
        };
    }

}
