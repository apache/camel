/**
 *
 */
package org.apache.camel.processor;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Processor;
import org.apache.camel.ValidationException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.model.TryType;

/**
 * Test handleAll
 *
 * @author <a href="mailto:nsandhu@raleys.com">nsandhu</a>
 */
public class ValidationHandleAllTest extends ContextTestSupport {
    protected Processor validator = new MyValidator();
    protected MockEndpoint validEndpoint;
    protected MockEndpoint invalidEndpoint;
    protected MockEndpoint allEndpoint;

    public void testValidMessage() throws Exception {
        validEndpoint.expectedMessageCount(1);
        invalidEndpoint.expectedMessageCount(0);
        allEndpoint.expectedMessageCount(1);

        template.sendBodyAndHeader("direct:start", "<valid/>", "foo", "bar");

        MockEndpoint.assertIsSatisfied(validEndpoint, invalidEndpoint, allEndpoint);
    }

    public void testInvalidMessage() throws Exception {
        invalidEndpoint.expectedMessageCount(1);
        validEndpoint.expectedMessageCount(0);
        allEndpoint.expectedMessageCount(1);

        template.sendBodyAndHeader("direct:start", "<invalid/>", "foo", "notMatchedHeaderValue");

        MockEndpoint.assertIsSatisfied(validEndpoint, invalidEndpoint, allEndpoint);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        validEndpoint = resolveMandatoryEndpoint("mock:valid", MockEndpoint.class);
        invalidEndpoint = resolveMandatoryEndpoint("mock:invalid", MockEndpoint.class);
        allEndpoint = resolveMandatoryEndpoint("mock:all", MockEndpoint.class);
    }

    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                TryType tryType = from("direct:start").tryBlock().
                        process(validator).
                        to("mock:valid");
                tryType.handle(ValidationException.class).to("mock:invalid");
                tryType.handleAll().to("mock:all");
            }
        };
    }
}
