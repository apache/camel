package org.apache.camel.component.sjms.producer;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.sjms.support.JmsTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class SjmsToDSendDynamicTwoDisabledTest extends JmsTestSupport {

    @Test
    public void testToD() throws Exception {
        template.sendBodyAndHeader("direct:start", "Hello bar", "where", "bar");
        template.sendBodyAndHeader("direct:start", "Hello beer", "where", "beer");
        template.sendBodyAndHeader("direct:start", "Hello gin", "where", "gin");

        template.sendBodyAndHeader("direct:start2", "Hello beer", "where2", "beer");
        template.sendBodyAndHeader("direct:start2", "Hello whiskey", "where2", "whiskey");

        // there should be 4 sjms endpoint
        long count = context.getEndpoints().stream().filter(e -> e.getEndpointUri().startsWith("sjms:")).count();
        assertEquals(4, count, "There should be 4 sjms endpoint");
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // route message dynamic using toD but turn off send dynamic aware
                from("direct:start").toD().allowOptimisedComponents(false).uri("sjms:queue:${header.where}");
                from("direct:start2").toD().allowOptimisedComponents(false).uri("sjms:queue:${header.where2}");
            }
        };
    }

}
