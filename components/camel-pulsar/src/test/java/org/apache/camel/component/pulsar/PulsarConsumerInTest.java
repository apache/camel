package org.apache.camel.component.pulsar;

import org.apache.camel.Endpoint;
import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.JndiRegistry;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.apache.pulsar.client.api.PulsarClient;
import org.apache.pulsar.client.impl.ClientBuilderImpl;
import org.junit.Test;

public class PulsarConsumerInTest extends CamelTestSupport {

    @EndpointInject(uri = "pulsar://persistent/omega-pl/fulfilment/BatchCreated?numberOfConsumers=10"
        + "&subscriptionName=batch-created-subscription&subscriptionType=Shared&consumerNamePrefix=test-consumer"
        + "&pulsarClient=#pulsarClient&consumerQueueSize=5&producerName=test-producer"
    )
    private Endpoint from;

    @EndpointInject(uri = "pulsar://persistent/omega/stock/BookIn?subscriptionName=book-stock"
        + "&subscriptionType=Shared&consumerNamePrefix=book-stock-consumer&numberOfConsumers=10"
        + "&pulsarClient=#pulsarClient&producerName=book-stock-producer&consumerQueueSize=5"
    )
    private Endpoint to;

    @EndpointInject(uri = "mock:result")
    private MockEndpoint mockEndpoint;

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {

            Processor processor = new Processor() {
                @Override
                public void process(Exchange exchange) {
                    System.out.println("Thread:: " + Thread.currentThread().getId() + " MSG:: "+ exchange.getIn().getBody());
                }
            };

            @Override
            public void configure() {
                from(from).to(to);
                from(to).to(mockEndpoint).unmarshal().string().process(processor);
            }
        };
    }

    @Override
    protected JndiRegistry createRegistry() throws Exception {
        JndiRegistry jndi = super.createRegistry();

        PulsarClient pulsarClient = new ClientBuilderImpl()
            .serviceUrl("pulsar://localhost:6650")
            .ioThreads(5)
            .listenerThreads(5)
            .build();

        jndi.bind("pulsarClient", pulsarClient);
        jndi.bind("pulsar", new PulsarComponent(context()));

        return jndi;
    }

    @Test
    public void test() {
        while (true) {
            //template.request(from, processor);
        }
    }
}
