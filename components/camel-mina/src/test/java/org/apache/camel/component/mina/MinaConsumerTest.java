package org.apache.camel.component.mina;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Processor;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;

/**
 * Unit test for wiki documentation
 */
public class MinaConsumerTest extends ContextTestSupport {

    public void testSendTextlineText() throws Exception {
        // START SNIPPET: e2
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Hello World");

        template.sendBody("mina:tcp://localhost:6200?textline=true", "Hello World\n");

        assertMockEndpointsSatisifed();
        // END SNIPPET: e2
    }

    public void testSendTextlineSyncText() throws Exception {
        // START SNIPPET: e4
        String response = (String)template.sendBody("mina:tcp://localhost:6201?textline=true&sync=true", "World\n");
        assertEquals("Bye World", response);
        // END SNIPPET: e4
    }

    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                // START SNIPPET: e1
                from("mina:tcp://localhost:6200?textline=true").to("mock:result");
                // END SNIPPET: e1

                // START SNIPPET: e3
                from("mina:tcp://localhost:6201?textline=true&sync=true").process(new Processor() {
                    public void process(Exchange exchange) throws Exception {
                        String body = exchange.getIn().getBody(String.class);
                        exchange.getOut().setBody("Bye " + body + "\n");
                    }
                });
                // END SNIPPET: e3
            }
        };
    }
}
