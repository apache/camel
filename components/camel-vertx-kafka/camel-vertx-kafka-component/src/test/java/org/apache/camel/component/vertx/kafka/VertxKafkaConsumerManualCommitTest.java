package org.apache.camel.component.vertx.kafka;

import java.io.IOException;
import java.util.Collections;
import java.util.Properties;
import java.util.stream.StreamSupport;

import org.apache.camel.Endpoint;
import org.apache.camel.EndpointInject;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.vertx.kafka.offset.VertxKafkaManualCommit;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class VertxKafkaConsumerManualCommitTest extends BaseEmbeddedKafkaTest {

    public static final String TOPIC = "test";

    @EndpointInject("vertx-kafka:" + TOPIC
                    + "?groupId=group1&sessionTimeoutMs=30000&enableAutoCommit=false&"
                    + "allowManualCommit=true&interceptorClasses=org.apache.camel.component.vertx.kafka.MockConsumerInterceptor")
    private Endpoint from;

    @EndpointInject("mock:result")
    private MockEndpoint to;

    private org.apache.kafka.clients.producer.KafkaProducer<String, String> producer;

    @BeforeEach
    public void before() {
        Properties props = getDefaultProperties();
        producer = new org.apache.kafka.clients.producer.KafkaProducer<>(props);
    }

    @AfterEach
    public void after() {
        if (producer != null) {
            producer.close();
        }
        // clean all test topics
        kafkaAdminClient.deleteTopics(Collections.singletonList(TOPIC));
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {

            @Override
            public void configure() throws Exception {
                from(from).routeId("foo").to(to).process(e -> {
                    VertxKafkaManualCommit manual
                            = e.getIn().getHeader(VertxKafkaConstants.MANUAL_COMMIT, VertxKafkaManualCommit.class);
                    assertNotNull(manual);
                    //manual.commitSync();
                });
            }
        };
    }

    @Test
    public void kafkaManualCommit() throws InterruptedException, IOException {
        to.expectedMessageCount(5);
        to.expectedBodiesReceivedInAnyOrder("message-0", "message-1", "message-2", "message-3", "message-4");
        // The LAST_RECORD_BEFORE_COMMIT header should include a value as we use
        // manual commit
        to.allMessages().header(VertxKafkaConstants.LAST_RECORD_BEFORE_COMMIT).isNotNull();

        for (int k = 0; k < 5; k++) {
            String msg = "message-" + k;
            ProducerRecord<String, String> data = new ProducerRecord<>(TOPIC, "1", msg);
            producer.send(data);
        }

        to.assertIsSatisfied(3000);

        assertEquals(5, StreamSupport.stream(MockConsumerInterceptor.recordsCaptured.get(0).records(TOPIC).spliterator(), false)
                .count());
    }
}
