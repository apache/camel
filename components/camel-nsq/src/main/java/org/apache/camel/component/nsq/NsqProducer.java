package org.apache.camel.component.nsq;

import com.github.brainlag.nsq.NSQProducer;
import com.github.brainlag.nsq.ServerAddress;
import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The nsq producer.
 */
public class NsqProducer extends DefaultProducer {

    private static final Logger LOG = LoggerFactory.getLogger(NsqProducer.class);

    private NSQProducer producer;
    private final NsqConfiguration configuration;

    public NsqProducer(NsqEndpoint endpoint) {
        super(endpoint);
        this.configuration = endpoint.getNsqConfiguration();
    }

    @Override
    public NsqEndpoint getEndpoint() {
        return (NsqEndpoint) super.getEndpoint();
    }

    @Override
    public void process(Exchange exchange) throws Exception {

        String topic = exchange.getIn().getHeader(NsqConstants.NSQ_MESSAGE_TOPIC,
                configuration.getTopic(), String.class);

        LOG.debug("Publishing to topic: {}", topic);

        byte[] body = exchange.getIn().getBody(byte[].class);
        producer.produce(topic, body);
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        LOG.debug("Starting NSQ Producer");

        NsqConfiguration config = getEndpoint().getNsqConfiguration();
        producer = new NSQProducer();
        for(ServerAddress server : config.getServerAddresses()) {
            producer.addAddress(server.getHost(),
                    server.getPort() == 0 ? config.getPort() : server.getPort());
        }
        producer.setConfig(getEndpoint().getNsqConfig());
        producer.start();
    }

    @Override
    protected void doStop() throws Exception {
        LOG.debug("Stopping NSQ Producer");
        if (producer != null) { producer.shutdown(); }
        super.doStop();
    }
}
