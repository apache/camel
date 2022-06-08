package org.apache.camel.component.hashicorp.vault.integration.operations;

import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

@Disabled("Disabled until we'll have a Camel-Hashicorp-vault test-infra module")
public class HashicorpProducerCreateSecretPOJOIT extends CamelTestSupport {

    @EndpointInject("mock:result")
    private MockEndpoint mock;

    @Test
    public void createSecretTest() {

        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:createSecret", new Processor() {
            @Override
            public void process(Exchange exchange) {
                Secrets sec = new Secrets();
                sec.username = "admin";
                sec.password = "password";
                exchange.getIn().setBody(sec);
            }
        });
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:createSecret")
                        .to("hashicorp-vault://secret?operation=createSecret&token=RAW(token)&host=localhost&scheme=http&secretPath=test")
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
