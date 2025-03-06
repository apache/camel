package org.apache.camel.component.kafka;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.component.kafka.consumer.KafkaManualCommit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ManualCommit implements Processor {

    private static final Logger LOG = LoggerFactory.getLogger(BatchManualCommit.class);

    @Override
    public void process(Exchange exchange) throws Exception {
        KafkaManualCommit manual = exchange.getMessage().getHeader(KafkaConstants.MANUAL_COMMIT, KafkaManualCommit.class);
        if (manual != null) {
            LOG.debug("Performing Kafka manual commit: {}", manual);
            manual.commit();
        } else {
            LOG.debug("Cannot perform Kafka manual commit due header: {} is missing", KafkaConstants.MANUAL_COMMIT);
        }
    }
}
