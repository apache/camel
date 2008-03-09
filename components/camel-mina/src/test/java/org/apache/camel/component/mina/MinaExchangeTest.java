package org.apache.camel.component.mina;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Processor;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;

/**
 * To unit test that we have access to MinaExchange and the Mina session object.
 *
 * @version $Revision$
 */
public class MinaExchangeTest extends ContextTestSupport {

    protected String uri = "mina:tcp://localhost:8080";

    public void testMinaRoute() throws Exception {
        MockEndpoint endpoint = getMockEndpoint("mock:result");
        Object body = "Hello there!";
        endpoint.expectedBodiesReceived(body);

        template.sendBody(uri, body);

        assertMockEndpointsSatisifed();
    }

    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from(uri).process(new Processor(){
                    public void process(Exchange exchange) throws Exception {
                        assertTrue("Should be MinaExchange", exchange instanceof MinaExchange);
                        MinaExchange me = (MinaExchange) exchange;
                        assertNotNull("IoSession should not be null", me.getSession());
                    }
                }).to("mock:result");
            }
        };
    }

}
