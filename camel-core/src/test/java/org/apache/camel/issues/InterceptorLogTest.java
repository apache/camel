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
        // TODO: we should only expect 1 message, but seda queues can sometimes send multiple
        mock.expectedMinimumMessageCount(1);
        mock.expectedBodiesReceived("Hello World");

        template.sendBody("seda:foo", "Hello World");

        mock.assertIsSatisfied();
    }

    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                // lets log all steps in all routes (must use proceed to let the exchange gots by its
                // normal route path instead of swalling it here by our interception
                intercept().to("log:foo").proceed();

                from("seda:foo").to("seda:bar");
                from("seda:bar").to("mock:result");
            }
        };
    }

}
