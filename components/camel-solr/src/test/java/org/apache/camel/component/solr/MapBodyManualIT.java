package org.apache.camel.component.solr;

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

@EnabledIfSystemProperty(named = "solr.address", matches = ".*",
                         disabledReason = "The Solr address (host:port) was not provided")
public class MapBodyManualIT extends CamelTestSupport {

    @EndpointInject
    private ProducerTemplate template;

    @EndpointInject("mock:result")
    private MockEndpoint result;

    @SuppressWarnings("unchecked")
    @Test
    public void sendIn() throws Exception {
        result.expectedMessageCount(1);

        template.send("direct:start", new Processor() {

            @Override
            public void process(Exchange exchange) throws Exception {
                Map<String, String> map = new HashMap<>();
                map.put("id", "0553579923");
                map.put("cat", "Test");
                map.put("name", "Test");
                map.put("price", "Test");
                map.put("author_t", "Test");
                map.put("series_t", "Test");
                map.put("sequence_i", "3");
                map.put("genre_s", "Test");
                exchange.getIn().setHeader(SolrConstants.OPERATION, SolrConstants.OPERATION_INSERT);
                exchange.getMessage().setBody(map);
            }
        });

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").to("solr://localhost:8984/solr/films").to("mock:result");
            }
        };
    }
}
