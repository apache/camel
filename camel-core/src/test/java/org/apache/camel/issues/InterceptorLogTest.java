package org.apache.camel.issues;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.builder.RouteBuilder;

/**
 * Testing http://activemq.apache.org/camel/dsl.html
 */
public class InterceptorLogTest extends ContextTestSupport {

    public void testInterceptor() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);

        template.sendBody("seda:foo", "Hello World");

        mock.assertIsSatisfied();
    }

    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                // lets log all steps in all routes
                // TODO: this does not work as expected. if enabled the exchange is not routed to seda:bar
                //intercept().to("log:foo");

                from("seda:foo").to("seda:bar");
                from("seda:bar").to("mock:result");
            }
        };
    }

}
