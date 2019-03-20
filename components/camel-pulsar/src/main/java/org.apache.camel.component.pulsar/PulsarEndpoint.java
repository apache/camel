package org.apache.camel.component.pulsar;

import org.apache.camel.Consumer;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.pulsar.configuration.PulsarEndpointConfiguration;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.support.DefaultEndpoint;
import org.apache.pulsar.client.api.PulsarClient;

@UriEndpoint(firstVersion = "2.2.1", scheme = "pulsar", title = "Apache Pulsar", syntax = "pulsar:topicType/tenant/namespace/topic", label = "messaging")
public class PulsarEndpoint extends DefaultEndpoint {

    @UriParam
    private final PulsarEndpointConfiguration pulsarEndpointConfiguration;
    @UriParam
    private final PulsarClient pulsarClient;

    public PulsarEndpoint(PulsarEndpointConfiguration pulsarEndpointConfiguration, PulsarClient pulsarClient) {
        this.pulsarEndpointConfiguration = pulsarEndpointConfiguration;
        this.pulsarClient = pulsarClient;
    }

    @Override
    public Producer createProducer() {
        return null;
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        PulsarConsumer consumer = PulsarConsumer.create(this, processor);
        configureConsumer(consumer);
        return consumer;
    }

    @Override
    public boolean isSingleton() {
        return false;
    }

    @Override
    public Exchange createExchange() {
        return super.createExchange();
    }

    public PulsarClient getPulsarClient() {
        return pulsarClient;
    }

    public PulsarEndpointConfiguration getConfiguration() {
        return pulsarEndpointConfiguration;
    }
}
