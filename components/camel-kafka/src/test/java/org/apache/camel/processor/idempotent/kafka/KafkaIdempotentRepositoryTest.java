package org.apache.camel.processor.idempotent.kafka;

import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.kafka.embedded.EmbeddedKafkaBroker;
import org.apache.camel.component.kafka.embedded.EmbeddedZookeeper;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Rule;
import org.junit.Test;

import java.util.Properties;

import static org.junit.Assert.*;

/**
 * @author jkorab
 */
public class KafkaIdempotentRepositoryTest extends CamelTestSupport {

    @Rule
    public EmbeddedZookeeper zookeeper = new EmbeddedZookeeper();

    @Rule
    public EmbeddedKafkaBroker kafkaBroker = new EmbeddedKafkaBroker(0, zookeeper.getConnection());

    @Override
    protected RoutesBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                KafkaIdempotentRepository kafkaIdempotentRepository = new KafkaIdempotentRepository();

                from("direct:in")
                    .idempotentConsumer(header("id"), kafkaIdempotentRepository)
                    .to("mock:out");
            }
        };
    }

    @Test
    public void testRemovesDuplicate() {
        
    }
}