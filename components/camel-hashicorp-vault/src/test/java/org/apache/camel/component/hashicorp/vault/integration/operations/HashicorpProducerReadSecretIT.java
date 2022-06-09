package org.apache.camel.component.hashicorp.vault.integration.operations;

import java.util.Map;

import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.hashicorp.vault.HashicorpVaultConstants;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@Disabled("Disabled until we'll have a Camel-Hashicorp-vault test-infra module")
public class HashicorpProducerReadSecretIT extends CamelTestSupport {

    @EndpointInject("mock:result")
    private MockEndpoint mock;

    @Test
    public void createSecretTest() throws InterruptedException {

        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:readSecret", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getMessage().setHeader(HashicorpVaultConstants.SECRET_PATH, "myapp");
            }
        });

        assertMockEndpointsSatisfied();
        Exchange ret = mock.getExchanges().get(0);
        assertNotNull(ret);
        assertEquals(((Map) ret.getMessage().getBody(Map.class).get("data")).get("id"), "12");
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:readSecret")
                        .to("hashicorp-vault://secret?operation=getSecret&token=RAW(token)&host=localhost&scheme=http")
                        .to("mock:result");
            }
        };
    }

    class Secrets {

        String username;
        String password;

        public String getUsername() {
            return username;
        }

        public String getPassword() {
            return password;
        }
    }
}
