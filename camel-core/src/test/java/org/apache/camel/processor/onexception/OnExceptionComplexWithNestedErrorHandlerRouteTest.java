package org.apache.camel.processor.onexception;

import org.apache.camel.builder.RouteBuilder;

public class OnExceptionComplexWithNestedErrorHandlerRouteTest extends OnExceptionComplexRouteTest {

    public void testNoError3() throws Exception {
        getMockEndpoint("mock:error").expectedMessageCount(0);
        getMockEndpoint("mock:result").expectedMessageCount(1);

        template.sendBody("direct:start3", "<order><type>myType</type><user>James</user></order>");

        assertMockEndpointsSatisfied();
    }

    public void testFunctionalError3() throws Exception {
        getMockEndpoint("mock:error").expectedMessageCount(0);
        getMockEndpoint("mock:error3").expectedMessageCount(1);
        getMockEndpoint("mock:result").expectedMessageCount(0);

        template.sendBody("direct:start3", "<order><type>myType</type><user>Func</user></order>");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // START SNIPPET: e1
                // global error handler
                errorHandler(deadLetterChannel("mock:error"));

                // shared for both routes
                onException(MyTechnicalException.class).handled(true).maximumRedeliveries(2).to("mock:tech.error");

                from("direct:start")
                    // route specific on exception for MyFunctionalException
                    // we MUST use .end() to indicate that this sub block is ended
                    .onException(MyFunctionalException.class).maximumRedeliveries(0).end()
                    .to("bean:myServiceBean")
                    .to("mock:result");

                from("direct:start2")
                    // route specific on exception for MyFunctionalException that is different than the previous route
                    // here we marked it as handled and send it to a different destination mock:handled
                    // we MUST use .end() to indicate that this sub block is ended
                    .onException(MyFunctionalException.class).handled(true).maximumRedeliveries(0).to("mock:handled").end()
                    .to("bean:myServiceBean")
                    .to("mock:result");

                from("direct:start3")
                    // route specific error handler that is different than the global error handler
                    // here we do not redeliver and transform the body to a simple text message with
                    // the exception message using the SimpleLanguage
                    // we MUST use .end() to indicate that this sub block is ended
                    .errorHandler(deadLetterChannel("mock:error3")
                            .maximumRedeliveries(0))

                    // route specific on exception for all exception to mark them as handled
                    .onException(Exception.class).handled(true).end()
                    .to("bean:myServiceBean")
                    .to("mock:result");
                // END SNIPPET: e1
            }
        };
    }
}