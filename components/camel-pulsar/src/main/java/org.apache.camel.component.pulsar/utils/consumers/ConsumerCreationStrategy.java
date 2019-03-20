package org.apache.camel.component.pulsar.utils.consumers;

import java.util.Collection;
import org.apache.camel.component.pulsar.PulsarEndpoint;
import org.apache.pulsar.client.api.Consumer;

public interface ConsumerCreationStrategy {

    Collection<Consumer<byte[]>> create(PulsarEndpoint pulsarEndpoint);
}
