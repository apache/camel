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
public class ValidationWithFinallyBlockPipelineTest extends ValidationTest {
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:start")
                    .tryBlock()
                        .process(validator)
                        .setHeader("valid", constant(true))
                    .handle(ValidationException.class)
                        .setHeader("valid", constant(false))
                    .finallyBlock()
                        .setBody(body())
                        .choice()
                        .when(header("valid").isEqualTo(true))
                        .to("mock:valid")
                        .otherwise()
                        .to("mock:invalid");
            }
        };
    }

}
