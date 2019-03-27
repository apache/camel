package org.apache.camel.component.pulsar;

import java.util.concurrent.TimeUnit;
import org.apache.camel.Endpoint;
import org.apache.camel.EndpointInject;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.JndiRegistry;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.apache.pulsar.client.api.PulsarClient;
import org.apache.pulsar.client.api.PulsarClientException;
import org.apache.pulsar.client.impl.ClientBuilderImpl;
import org.junit.Test;

public class PulsarProducerInTest extends CamelTestSupport {

    private static final String PULSAR_CLUSTER_URL = "pulsar://localhost:6650";
    private static final String PULSAR_CLIENT_BEAN_NAME = "pulsarClient";

    private static final String TOPIC_URI = "persistent://public/default/camel-producer-topic";
    private static final String PRODUCER = "camel-producer";

    @Produce(uri = "direct:start")
    private ProducerTemplate producerTemplate;

    @EndpointInject(uri = "pulsar:" + TOPIC_URI
        + "?numberOfConsumers=1&subscriptionType=Exclusive"
        + "&subscriptionName=camel-subscription&consumerQueueSize=1"
        + "&consumerName=camel-consumer"
        + "&producerName=" + PRODUCER
        + "&pulsarClient=#" + PULSAR_CLIENT_BEAN_NAME
    )
    private Endpoint from;

    @EndpointInject(uri = "mock:result")
    private MockEndpoint to;

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {

            @Override
            public void configure() {
                from("direct:start").to(from);
                from(from).to(to);
            }
        };
    }

    @Override
    protected JndiRegistry createRegistry() throws Exception {
        JndiRegistry jndi = super.createRegistry();

        registerPulsarBeans(jndi);

        return jndi;
    }

    private void registerPulsarBeans(final JndiRegistry jndi) throws PulsarClientException {
        PulsarClient pulsarClient = givenPulsarClient();

        jndi.bind("pulsarClient", pulsarClient);
        jndi.bind("pulsar", new PulsarComponent(context()));
    }

    private PulsarClient givenPulsarClient() throws PulsarClientException {
        return new ClientBuilderImpl()
            .serviceUrl(PULSAR_CLUSTER_URL)
            .ioThreads(1)
            .listenerThreads(1)
            .build();
    }

    @Test
    public void givenARunningPulsarCluster_whenIPublishAMessageToRoute_verifyMessageIsSentToClusterAndThenConsumed() throws Exception {
        to.expectedMessageCount(3);

        producerTemplate.sendBody("Hello ");
        producerTemplate.sendBody("World ");
        producerTemplate.sendBody(new Integer(10));

        MockEndpoint.assertIsSatisfied(10, TimeUnit.SECONDS, to);
    }
}
