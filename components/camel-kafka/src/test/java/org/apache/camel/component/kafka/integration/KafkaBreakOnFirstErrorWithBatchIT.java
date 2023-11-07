package org.apache.camel.component.kafka.integration;

import org.apache.camel.Endpoint;
import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.kafka.KafkaConstants;
import org.apache.camel.component.kafka.MockConsumerInterceptor;
import org.apache.camel.component.kafka.consumer.KafkaManualCommit;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.util.CamelKafkaUtil;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * this will test basic breakOnFirstError functionality
 */
class KafkaBreakOnFirstErrorWithBatchIT extends BaseEmbeddedKafkaTestSupport {

    private static final Logger LOG = LoggerFactory.getLogger(KafkaBreakOnFirstErrorWithBatchIT.class);

    private final List<String> errorPayloads = new CopyOnWriteArrayList<>();
    
    public static final String NO_ON_EXCEPTION_ROUTE_ID = "breakOnFirstErrorBatchIT";
    public static final String TOPIC = "test-foobar";

    @EndpointInject("kafka:" + TOPIC
            + "?groupId=KafkaBreakOnFirstErrorIT"
            + "&autoOffsetReset=earliest"
            + "&autoCommitEnable=false"
            + "&allowManualCommit=true"
            + "&breakOnFirstError=true"
            + "&maxPollRecords=3"
            + "&pollTimeoutMs=1000"
            + "&keyDeserializer=org.apache.kafka.common.serialization.StringDeserializer"
            + "&valueDeserializer=org.apache.kafka.common.serialization.StringDeserializer"
            + "&interceptorClasses=org.apache.camel.component.kafka.MockConsumerInterceptor")
    private Endpoint from;

    @EndpointInject("mock:result")
    private MockEndpoint to;

    private org.apache.kafka.clients.producer.KafkaProducer<String, String> producer;

    @BeforeEach
    public void before() {
        Properties props = getDefaultProperties();
        producer = new org.apache.kafka.clients.producer.KafkaProducer<>(props);
        MockConsumerInterceptor.recordsCaptured.clear();
    }

    @AfterEach
    public void after() {
        if (producer != null) {
            producer.close();
        }
        // clean all test topics
        kafkaAdminClient.deleteTopics(Collections.singletonList(TOPIC)).all();
    }
    
    /**
     * will continue to retry the message that is in error
     */
    @Test
    public void kafkaBreakOnFirstErrorBasicCapabilityWithoutOnExcepton() throws Exception {
        to.reset();
        to.expectedMessageCount(3);
        // message-3 causes an error 
        // and breakOnFirstError will cause it to be retried forever
        // we will never get to message-4
        to.expectedBodiesReceived("message-0", "message-1", "message-2");

        this.publishMessagesToKafka();
        
        context.getRouteController().stopRoute(NO_ON_EXCEPTION_ROUTE_ID);
        context.getRouteController().startRoute(NO_ON_EXCEPTION_ROUTE_ID);
        
        Awaitility.await()
            .atMost(3, TimeUnit.SECONDS)
            .until(() -> errorPayloads.size() > 3);

        to.assertIsSatisfied();
        
        for (String payload : errorPayloads) {
            assertEquals("message-3", payload);
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {

            @Override
            public void configure() {
                from(from)
                    .routeId(NO_ON_EXCEPTION_ROUTE_ID)
                    .process(exchange -> {
                        LOG.debug(CamelKafkaUtil.buildKafkaLogMessage("Consuming", exchange, true));
                    })
                    .process(exchange -> {
                        ifIsPayloadWithErrorThrowException(exchange);
                    })
                    .to(to)
                    .process(exchange -> {
                        doCommitOffset(exchange);
                    });
            }
        };
    }

    private void publishMessagesToKafka() {
        for (int i = 0; i < 5; i++) {
            String msg = "message-" + i;
            ProducerRecord<String, String> data = new ProducerRecord<>(TOPIC, null, msg);
            producer.send(data);
        }
    }
    
    private void ifIsPayloadWithErrorThrowException(Exchange exchange) {
        String payload = exchange.getMessage().getBody(String.class);
        if (payload.equals("message-3")) {
            errorPayloads.add(payload);
            throw new RuntimeException("ERROR TRIGGERED BY TEST");
        }
    }
    
    private void doCommitOffset(Exchange exchange) {
        LOG.debug(CamelKafkaUtil.buildKafkaLogMessage("Committing", exchange, true));
        KafkaManualCommit manual = exchange.getMessage()
            .getHeader(KafkaConstants.MANUAL_COMMIT, KafkaManualCommit.class);
        assertNotNull(manual);
        manual.commit();
    }
    
}
