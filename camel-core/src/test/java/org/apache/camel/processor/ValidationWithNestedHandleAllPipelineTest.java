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
public class ValidationWithNestedHandleAllPipelineTest extends ValidationTest {
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:start")
                    .tryBlock()
                        .to("direct:embedded")
                    .handle(ValidationException.class)
                        .to("mock:invalid");
                
                from("direct:embedded")
                    .errorHandler(noErrorHandler())
                    .tryBlock()
                        .process(validator)
                        .to("mock:valid")
                    .handleAll()
                        .setHeader("valid", constant(false))
                    .end();
            }
        };
    }

}
