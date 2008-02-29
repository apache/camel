/**
 * 
 */
package org.apache.camel.processor;

import org.apache.camel.ValidationException;
import org.apache.camel.builder.RouteBuilder;

/**
 * The handle catch clause has a pipeline processing the exception.
 * 
 * @author <a href="mailto:nsandhu">nsandhu</a>
 * 
 */
public class ValidationWithHandlePipelineAndExceptionTest extends ValidationTest {
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                exception(ValidationException.class).to("mock:invalid");
                
                from("direct:start").tryBlock().process(validator).to("mock:valid").handle(
                        ValidationException.class).process(validator);
            }
        };
    }

}
