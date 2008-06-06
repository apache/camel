package org.apache.camel.component.velocity;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Message;
import org.apache.camel.Exchange;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.builder.RouteBuilder;

/**
 * Unit test for wiki documentation
 */
public class VelocityLetterTest extends ContextTestSupport {

    // START SNIPPET: e1
    private Exchange createLetter() {
        Exchange exchange = context.getEndpoint("direct:a").createExchange();
        Message msg = exchange.getIn();
        msg.setHeader("firstName", "Claus");
        msg.setHeader("lastName", "Ibsen");
        msg.setHeader("item", "Camel in Action");
        return exchange;
    }

    public void testVelocityLetter() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.expectedBodiesReceived("Dear Ibsen, Claus\n\nThanks for the order of Camel in Action.\n\nRegards Camel Riders Bookstore");

        template.send("direct:a", createLetter());

        mock.assertIsSatisfied();
    }

    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from("direct:a").to("velocity:org/apache/camel/component/velocity/letter.vm").to("mock:result");
            }
        };
    }
    // END SNIPPET: e1
}
