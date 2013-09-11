package org.apache.camel.component.infinispan;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Ignore;
import org.junit.Test;

@Ignore //start local server with: ./bin/startServer.sh -r hotrod
public class InfinispanRemoteProducerTest extends CamelTestSupport {

    @Test
    public void producerPublishesKeyAndValue() throws Exception {
        Exchange request = template.request("direct:start", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(InfinispanConstants.KEY, "keyOne");
                exchange.getIn().setHeader(InfinispanConstants.VALUE, "valueOne");
            }
        });

        assertNull(request.getException());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {
                from("direct:start")
                        .to("infinispan://localhost");
            }
        };
    }
}
