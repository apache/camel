package org.apache.camel.builder.endpoint;

import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class LambdaEndpointRouteBuilderTest extends BaseEndpointDslTest {

    @Test
    public void testLambda() throws Exception {
        assertEquals(0, context.getRoutesSize());

        LambdaEndpointRouteBuilder builder = rb -> rb.from(rb.direct("start")).to(rb.mock("result"));
        context.addRoutes(new EndpointRouteBuilder(context) {
            @Override
            public void configure() throws Exception {
                builder.accept(this);
            }
        });
        context.start();

        assertEquals(1, context.getRoutesSize());

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Hello World");

        template.sendBody("direct:start", "Hello World");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testLambdaTwo() throws Exception {
        assertEquals(0, context.getRoutesSize());

        EndpointRouteBuilder.addEndpointRoutes(context, rb -> rb.from(rb.direct("start")).to(rb.mock("result")));

        context.start();

        assertEquals(1, context.getRoutesSize());

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Hello World");

        template.sendBody("direct:start", "Hello World");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testLambdaSimple() throws Exception {
        assertEquals(0, context.getRoutesSize());

        EndpointRouteBuilder.addEndpointRoutes(context,
                rb -> rb.from(rb.direct("start")).transform(rb.simple("Hello ${body}")).to(rb.mock("result")));

        context.start();

        assertEquals(1, context.getRoutesSize());

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Hello World");

        template.sendBody("direct:start", "World");

        assertMockEndpointsSatisfied();
    }

}
