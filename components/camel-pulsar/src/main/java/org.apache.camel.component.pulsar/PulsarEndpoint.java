package org.apache.camel.component.pulsar;

import org.apache.camel.Consumer;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.pulsar.configuration.PulsarEndpointConfiguration;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.pulsar.client.api.PulsarClient;
import org.apache.pulsar.client.api.PulsarClientException;

@UriEndpoint(scheme = "pulsar", title = "Apache Pulsar", syntax = "pulsar:tenant/namespace/topic", label = "messaging")
public class PulsarEndpoint extends DefaultEndpoint {

    @UriParam
    private final PulsarEndpointConfiguration pulsarEndpointConfiguration;
    @UriParam
    private final PulsarClient pulsarClient;
    @UriPath(label = "consumer,producer", description = "Topic uri path")
    private final String topic;

    private PulsarEndpoint(String uri, String path, PulsarEndpointConfiguration pulsarEndpointConfiguration, PulsarComponent component) throws PulsarClientException {
        super(uri, component);
        // TODO: convert path to pulsar uri (include persistent / non-persistent prefix)
        this.topic = path;
        this.pulsarEndpointConfiguration = pulsarEndpointConfiguration;
        this.pulsarClient = pulsarEndpointConfiguration.getPulsarClient();
    }

    public static PulsarEndpoint create(String uri, String path, PulsarEndpointConfiguration pulsarEndpointConfiguration, PulsarComponent component) throws PulsarClientException {
        return new PulsarEndpoint(uri, path, pulsarEndpointConfiguration, component);
    }

    @Override
    public Producer createProducer() {
        return PulsarProducer.create(this);
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

    public String getTopic() {
        return topic;
    }
}
