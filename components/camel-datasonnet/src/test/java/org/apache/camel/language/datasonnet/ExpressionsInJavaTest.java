package org.apache.camel.language.datasonnet;

import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.skyscreamer.jsonassert.JSONAssert;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ExpressionsInJavaTest extends CamelTestSupport {
    @EndpointInject("mock:direct:response")
    protected MockEndpoint endEndpoint;

    @Produce("direct:expressionsInJava")
    protected ProducerTemplate expressionsInJavaProducer;

    @Produce("direct:chainExpressions")
    protected ProducerTemplate chainExpressionsProducer;

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
            return new RouteBuilder() {
                    @Override
                    public void configure() throws Exception {
                        from("direct:chainExpressions")
                                .setHeader("ScriptHeader", constant("{ hello: \"World\"}"))
                                .setBody(datasonnet(simple("${header.ScriptHeader}")))
                                .to("mock:direct:response");

                        from("direct:expressionsInJava")
                                .choice()
                                    .when(datasonnet("payload == 'World'", "text/plain", "application/json"))
                                        .setBody(datasonnet("'Hello, ' + payload", "text/plain", "text/plain"))
                                    .otherwise()
                                        .setBody(datasonnet("'Good bye, ' + payload", "text/plain", "text/plain"))
                                    .end()
                                .to("mock:direct:response");
                    }
                };
    }

    @Test
    public void testExpressionLanguageInJava() throws Exception {
        endEndpoint.expectedMessageCount(1);
        expressionsInJavaProducer.sendBody("World");
        Exchange exchange = endEndpoint.assertExchangeReceived(endEndpoint.getReceivedCounter() - 1);
        String response = exchange.getIn().getBody().toString();
        assertEquals("Hello, World", response);
    }

    @Test
    public void testChainExpressions() throws Exception {
        endEndpoint.expectedMessageCount(1);
        chainExpressionsProducer.sendBody("{}");
        Exchange exchange = endEndpoint.assertExchangeReceived(endEndpoint.getReceivedCounter() - 1);
        String response = exchange.getIn().getBody().toString();
        JSONAssert.assertEquals("{\"hello\":\"World\"}", response, true);
    }
}
