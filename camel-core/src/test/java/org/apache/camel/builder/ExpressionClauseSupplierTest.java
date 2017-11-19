package org.apache.camel.builder;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.component.mock.MockEndpoint;

public class ExpressionClauseSupplierTest extends ContextTestSupport {

    private static final String BODY_SUPPLIER_MSG = "I am the body supplier!";
    private static final String INMESSAGE_SUPPLIER_MSG = "I am the in-message supplier!";


    public void testBodySupplier() throws Exception {
        MockEndpoint functionMock1 = getMockEndpoint("mock:output1");
        functionMock1.expectedMessageCount(1);
        functionMock1.expectedBodyReceived().constant(BODY_SUPPLIER_MSG);

        template.sendBody("direct:supplier1", "are you there?");

        assertMockEndpointsSatisfied();
    }

    public void testInMessageSupplier() throws Exception {
        MockEndpoint functionMock2 = getMockEndpoint("mock:output2");
        functionMock2.expectedMessageCount(1);
        functionMock2.expectedBodyReceived().constant(INMESSAGE_SUPPLIER_MSG);

        template.sendBody("direct:supplier2", "who are you?");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:supplier1")
                    .transform().body(() -> BODY_SUPPLIER_MSG)
                    .to("mock:output1");

                from("direct:supplier2")
                    .transform().inMessage(() -> INMESSAGE_SUPPLIER_MSG)
                    .to("mock:output2");
            }
        };
    }

}
