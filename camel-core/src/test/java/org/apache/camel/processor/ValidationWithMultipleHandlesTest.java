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
public class ValidationWithMultipleHandlesTest extends ValidationTest {
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:start")
                    .tryBlock()
                        .process(validator)
                    .handle(ValidationException.class)
                        .setHeader("xxx", constant("yyy"))
                    .end()
                    .tryBlock()
                        .process(validator).to("mock:valid")
                    .handle(ValidationException.class)
                        .pipeline("direct:a", "mock:invalid");
            }
        };
    }

}
