package org.apache.camel.model;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.AdviceWith;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.builder.RouteConfigurationBuilder;
import org.apache.camel.builder.TemplatedRouteBuilder;
import org.apache.camel.component.direct.DirectConsumerNotAvailableException;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

import java.net.ConnectException;

public class RouteConfigurationOnExceptionTest extends ContextTestSupport {

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    public void testGlobal() throws Exception {
        context.addRoutes(new RouteConfigurationBuilder() {
            @Override
            public void configuration() throws Exception {
                routeConfiguration("my-error-handler").onException(Exception.class)
                        .handled(true)
                        .log(LoggingLevel.ERROR, log, "--> Exception: ${exception.message}, Delivery was NOT rolled back");
            }
        });
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                routeTemplate("route-template-1")
                        .templateParameter("route_url-1")
                        .templateParameter("route_url-2")
                        .templateParameter("route-id-param-id")

                        .from("direct:{{route_url-1}}")
                        .routeConfigurationId("my-error-handler")
                        .end()

                        .log(LoggingLevel.INFO, log, "--> Executing")
                        .to("direct:{{route_url-2}}")
                        .id("{{route-id-param-id}}")
                        .log(LoggingLevel.INFO, log, "--> Executed!");
            }
        });

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {

                from("direct:start2")
                        .to("mock:result2");
            }
        });

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                final String routeId = TemplatedRouteBuilder.builder(context, "route-template-1")
                        .routeId("my-test-file-route")
                        .parameter("route_url-1", "start")
                        .parameter("route_url-2", "startWrong")
                        .parameter("route-id-param-id", "my-internal-route-id")
                        .add();
            }
        });

        MockEndpoint mockEndpoint = getMockEndpoint("mock:endpointMock");
        AdviceWith.adviceWith(context, "my-test-file-route", routeBuilder -> {
            routeBuilder.replaceFromWith("direct:start");
            routeBuilder.weaveAddLast().to(mockEndpoint);
            routeBuilder.setLogRouteAsXml(false);
        });

        context.start();

        final String testMsg = "{ test msg }";

        mockEndpoint.expectedMessageCount(0);
        //mockEndpoint.expectedBodiesReceived(testMsg);
        template.sendBody("direct:start", testMsg);
        mockEndpoint.assertIsSatisfied();

    }
}
