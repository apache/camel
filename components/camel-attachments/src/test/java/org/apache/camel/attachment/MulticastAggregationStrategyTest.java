package org.apache.camel.attachment;

import jakarta.activation.DataHandler;
import org.apache.camel.AggregationStrategy;
import org.apache.camel.Exchange;
import org.apache.camel.builder.Builder;
import org.apache.camel.builder.ExchangeBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class MulticastAggregationStrategyTest extends CamelTestSupport {

    @Test
    void testAggregationStrategyWithAttachment() {
        Exchange exchange = ExchangeBuilder.anExchange(new DefaultCamelContext()).build();
        
        template.send("direct:start", exchange);

        AttachmentMessage msg = exchange.getMessage(AttachmentMessage.class);
        
        assertTrue(msg.hasAttachments());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                    .multicast(new AttachmentMessageAggregationStrategy()).stopOnException()
                        .to("direct:setBody", "direct:setAttachment")
                    .end();

                from("direct:setBody")
                    .setBody(Builder.constant("body"));

                from("direct:setAttachment")
                    .setBody(Builder.constant("attachment".getBytes()));
            }
        };
    }
    
    private static class AttachmentMessageAggregationStrategy implements AggregationStrategy {

        @Override
        public Exchange aggregate(Exchange oldExchange, Exchange newExchange) {
            if (oldExchange == null) {
                return newExchange;
            }

            if (newExchange.getIn().getBody() instanceof String) {
                oldExchange.getMessage().setBody(newExchange.getIn().getBody());
            } else {
                byte[] data = newExchange.getIn().getBody(byte[].class);
                oldExchange.getMessage(AttachmentMessage.class).addAttachment("attachment", new DataHandler(data, "text/plain"));
            }

            return oldExchange;
        }
    }
}
