/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.component.vertx.kafka;

import java.util.Collections;
import java.util.Map;
import java.util.Properties;

import io.vertx.core.buffer.Buffer;
import org.apache.camel.Endpoint;
import org.apache.camel.EndpointInject;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.vertx.kafka.serde.VertxKafkaHeaderSerializer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class VertxKafkaConsumerFullTest extends BaseEmbeddedKafkaTest {

    public static final String TOPIC = "test";

    @EndpointInject("vertx-kafka:" + TOPIC
                    + "?groupId=group1&autoOffsetReset=earliest&keyDeserializer=org.apache.kafka.common.serialization.StringDeserializer&"
                    + "valueDeserializer=org.apache.kafka.common.serialization.StringDeserializer"
                    + "&autoCommitIntervalMs=1000&sessionTimeoutMs=30000")
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
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {

            @Override
            public void configure() {
                from(from).routeId("foo").to(to);
            }
        };
    }

    @Test
    public void kafkaMessageIsConsumedByCamel() throws InterruptedException {
        String propagatedHeaderKey = "PropagatedCustomHeader";
        byte[] propagatedHeaderValue = "propagated header value".getBytes();
        String skippedHeaderKey = "CamelSkippedHeader";
        to.expectedMessageCount(5);
        to.expectedBodiesReceivedInAnyOrder("message-0", "message-1", "message-2", "message-3", "message-4");
        to.expectedHeaderReceived(propagatedHeaderKey, propagatedHeaderValue);

        for (int k = 0; k < 5; k++) {
            String msg = "message-" + k;
            ProducerRecord<String, String> data = new ProducerRecord<>(TOPIC, "1", msg);
            data.headers().add(new RecordHeader("CamelSkippedHeader", "skipped header value".getBytes()));
            data.headers().add(new RecordHeader(propagatedHeaderKey, propagatedHeaderValue));
            producer.send(data);
        }

        to.assertIsSatisfied(3000);

        Map<String, Object> headers = to.getExchanges().get(0).getIn().getHeaders();
        assertFalse(headers.containsKey(skippedHeaderKey), "Should not receive skipped header");
        assertTrue(headers.containsKey(propagatedHeaderKey), "Should receive propagated header");
    }

    @Test
    public void kafkaRecordSpecificHeadersAreNotOverwritten() throws InterruptedException {
        String propagatedHeaderKey = VertxKafkaConstants.TOPIC;
        byte[] propagatedHeaderValue = "propagated incorrect topic".getBytes();
        to.expectedHeaderReceived(VertxKafkaConstants.TOPIC, TOPIC);

        String propagatedStringHeaderKey = "propagatedStringHeaderKey";
        byte[] propagatedStringHeaderValue = "propagated value".getBytes();

        String propagatedLongHeaderKey = "propagatedLongHeaderKey";
        byte[] propagatedLongHeaderValue = VertxKafkaHeaderSerializer.serialize(5454545454545L).getBytes();

        ProducerRecord<String, String> data = new ProducerRecord<>(TOPIC, "1", "message");
        data.headers().add(new RecordHeader(propagatedHeaderKey, propagatedHeaderValue));
        data.headers().add(new RecordHeader(propagatedStringHeaderKey, propagatedStringHeaderValue));
        data.headers().add(new RecordHeader(propagatedLongHeaderKey, propagatedLongHeaderValue));
        producer.send(data);

        to.assertIsSatisfied(3000);

        Map<String, Object> headers = to.getExchanges().get(0).getIn().getHeaders();
        assertTrue(headers.containsKey(VertxKafkaConstants.TOPIC), "Should receive KafkaEndpoint populated kafka.TOPIC header");
        assertEquals(TOPIC, headers.get(VertxKafkaConstants.TOPIC), "Topic name received");
        assertEquals("propagated value", ((Buffer) headers.get("propagatedStringHeaderKey")).toString());
        assertEquals(5454545454545L, ((Buffer) headers.get("propagatedLongHeaderKey")).getLong(0));
    }

    @Test
    public void kafkaMessageIsConsumedByCamelSeekedToBeginning() throws Exception {
        to.expectedMessageCount(5);
        to.expectedBodiesReceivedInAnyOrder("message-0", "message-1", "message-2", "message-3", "message-4");
        for (int k = 0; k < 5; k++) {
            String msg = "message-" + k;
            ProducerRecord<String, String> data = new ProducerRecord<>(TOPIC, "1", msg);
            producer.send(data);
        }
        to.assertIsSatisfied(3000);

        to.reset();

        to.expectedMessageCount(5);
        to.expectedBodiesReceivedInAnyOrder("message-0", "message-1", "message-2", "message-3", "message-4");

        // Restart endpoint,
        context.getRouteController().stopRoute("foo");

        VertxKafkaEndpoint kafkaEndpoint = (VertxKafkaEndpoint) from;
        kafkaEndpoint.getConfiguration().setSeekToPosition("beginning");

        context.getRouteController().startRoute("foo");

        // As wee set seek to beginning we should re-consume all messages
        to.assertIsSatisfied(3000);
    }

    @Test
    public void kafkaMessageIsConsumedByCamelSeekedToEnd() throws Exception {
        to.expectedMessageCount(5);
        to.expectedBodiesReceivedInAnyOrder("message-0", "message-1", "message-2", "message-3", "message-4");
        for (int k = 0; k < 5; k++) {
            String msg = "message-" + k;
            ProducerRecord<String, String> data = new ProducerRecord<>(TOPIC, "1", msg);
            producer.send(data);
        }
        to.assertIsSatisfied(3000);

        to.reset();

        to.expectedMessageCount(0);

        // Restart endpoint,
        context.getRouteController().stopRoute("foo");

        VertxKafkaEndpoint kafkaEndpoint = (VertxKafkaEndpoint) from;
        kafkaEndpoint.getConfiguration().setSeekToPosition("end");

        context.getRouteController().startRoute("foo");

        // As wee set seek to end we should not re-consume any messages
        synchronized (this) {
            Thread.sleep(1000);
        }
        to.assertIsSatisfied(3000);
    }
}
