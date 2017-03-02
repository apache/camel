package org.apache.camel.processor.idempotent.kafka;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelExecutionException;
import org.apache.camel.EndpointInject;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.kafka.embedded.EmbeddedKafkaBroker;
import org.apache.camel.component.kafka.embedded.EmbeddedZookeeper;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.SimpleRegistry;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Rule;
import org.junit.Test;

/**
 * @author jkorab
 */
public class KafkaIdempotentRepositoryEagerTest extends CamelTestSupport {

    @Rule
    public EmbeddedZookeeper zookeeper = new EmbeddedZookeeper();

    @Rule
    public EmbeddedKafkaBroker kafkaBroker = new EmbeddedKafkaBroker(0, zookeeper.getConnection());

    private KafkaIdempotentRepository kafkaIdempotentRepository;

    @Override
    protected CamelContext createCamelContext() throws Exception {
        SimpleRegistry registry = new SimpleRegistry();

        kafkaIdempotentRepository = new KafkaIdempotentRepository("TEST_IDEM", kafkaBroker.getBrokerList());
        registry.put("kafkaIdempotentRepository", kafkaIdempotentRepository);

        return new DefaultCamelContext(registry);
    }

    @Override
    protected RoutesBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:in")
                    .to("mock:before")
                    .idempotentConsumer(header("id")).messageIdRepositoryRef("kafkaIdempotentRepository")
                        .to("mock:out")
                    .end();
            }
        };
    }

    @EndpointInject(uri = "mock:out")
    MockEndpoint mockOut;

    @EndpointInject(uri = "mock:before")
    MockEndpoint mockBefore;

    @Test
    public void testRemovesDuplicates() throws InterruptedException {
        for (int i = 0; i < 10; i++) {
            template.sendBodyAndHeader("direct:in", "Test message", "id", i % 5);
        }

        assertEquals(5, kafkaIdempotentRepository.getDuplicateCount());

        assertEquals(5, mockOut.getReceivedCounter());
        assertEquals(10, mockBefore.getReceivedCounter());
    }

    @Test
    public void testRollsBackOnException() throws InterruptedException {
        mockOut.whenAnyExchangeReceived((exchange -> {
            int id = exchange.getIn().getHeader("id", Integer.class);
            if (id == 0) {
                throw new IllegalArgumentException("Boom!");
            }
        }));

        for (int i = 0; i < 10; i++) {
            try {
                template.sendBodyAndHeader("direct:in", "Test message", "id", i % 5);
            } catch (CamelExecutionException cex) {
                // no-op; expected
            }
        }

        assertEquals(4, kafkaIdempotentRepository.getDuplicateCount()); // id{0} is not a duplicate

        assertEquals(6, mockOut.getReceivedCounter()); // id{0} goes through the idempotency check twice
        assertEquals(10, mockBefore.getReceivedCounter());
    }

    @Test
    public void testClear() throws InterruptedException {
        mockOut.setExpectedMessageCount(2);

        template.sendBodyAndHeader("direct:in", "Test message", "id", 0);
        kafkaIdempotentRepository.clear();
        template.sendBodyAndHeader("direct:in", "Test message", "id", 0);

        assertMockEndpointsSatisfied();
    }
}