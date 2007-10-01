/**
 *
 */
package org.apache.camel.processor;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.model.TryType;

/**
 * No catch blocks but handle all should work
 *
 * @author <a href="mailto:nsandhu@raleys.com">nsandhu</a>
 */
public class ValidationHandleAllNoCatchTest extends ContextTestSupport {
    protected Processor validator = new MyValidator();
    protected MockEndpoint validEndpoint;
    protected MockEndpoint allEndpoint;

    public void testValidMessage() throws Exception {
        validEndpoint.expectedMessageCount(1);
        allEndpoint.expectedMessageCount(1);

        template.sendBodyAndHeader("direct:start", "<valid/>", "foo", "bar");

        MockEndpoint.assertIsSatisfied(validEndpoint, allEndpoint);
    }

    public void testInvalidMessage() throws Exception {
        validEndpoint.expectedMessageCount(0);
        allEndpoint.expectedMessageCount(1);

        template.sendBodyAndHeader("direct:start", "<invalid/>", "foo", "notMatchedHeaderValue");

        MockEndpoint.assertIsSatisfied(validEndpoint, allEndpoint);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        validEndpoint = resolveMandatoryEndpoint("mock:valid", MockEndpoint.class);
        allEndpoint = resolveMandatoryEndpoint("mock:all", MockEndpoint.class);
    }

    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                TryType tryType = from("direct:start").tryBlock().
                        process(validator).
                        to("mock:valid");
                tryType.handleAll().to("mock:all");
            }
        };
    }
}
