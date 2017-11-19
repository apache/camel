package org.apache.camel.model;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;

public class ProcessDefinitionSetBodyTest extends ContextTestSupport {

    private final String SUPPLIER_MESSAGE = "Hello from the Supplier!";
    private final String FUNCTION_MESSAGE = "Hello from the Function!";

    public void testProcessDefinitionSetBody() throws InterruptedException {

        MockEndpoint functionMock1 = getMockEndpoint("mock:supplierOutput");
        functionMock1.expectedMessageCount(1);
        functionMock1.expectedBodyReceived().constant(SUPPLIER_MESSAGE);
        MockEndpoint functionMock2 = getMockEndpoint("mock:functionOutput");
        functionMock2.expectedMessageCount(1);
        functionMock2.expectedBodyReceived().constant(FUNCTION_MESSAGE);

        template.sendBody("direct:start", "are you there?");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                    .setBody(() -> SUPPLIER_MESSAGE)
                    .to("mock:supplierOutput")
                    .setBody(exchange -> FUNCTION_MESSAGE)
                    .to("mock:functionOutput")
                    .to("mock:output");
            }
        };
    }
}
