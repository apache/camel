/**
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
package org.apache.camel.component.kafka;

import java.io.IOException;
import java.util.Properties;
import java.util.stream.StreamSupport;

import org.apache.camel.Endpoint;
import org.apache.camel.EndpointInject;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

public class KafkaConsumerFullTest extends BaseEmbeddedKafkaTest {

    public static final String TOPIC = "test";

    @EndpointInject(uri = "kafka:" + TOPIC
            + "?groupId=group1&autoOffsetReset=earliest&keyDeserializer=org.apache.kafka.common.serialization.StringDeserializer&"
            + "valueDeserializer=org.apache.kafka.common.serialization.StringDeserializer"
            + "&autoCommitIntervalMs=1000&sessionTimeoutMs=30000&autoCommitEnable=true&interceptorClasses=org.apache.camel.component.kafka.MockConsumerInterceptor")
    private Endpoint from;

    @EndpointInject(uri = "mock:result")
    private MockEndpoint to;

    private org.apache.kafka.clients.producer.KafkaProducer<String, String> producer;

    @Before
    public void before() {
        Properties props = getDefaultProperties();
        producer = new org.apache.kafka.clients.producer.KafkaProducer<String, String>(props);
    }

    @After
    public void after() {
        if (producer != null) {
            producer.close();
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {

            @Override
            public void configure() throws Exception {
                from(from).routeId("foo").to(to);
            }
        };
    }

    @Test
    public void kafkaMessageIsConsumedByCamel() throws InterruptedException, IOException {
        to.expectedMessageCount(5);
        to.expectedBodiesReceivedInAnyOrder("message-0", "message-1", "message-2", "message-3", "message-4");
        // The LAST_RECORD_BEFORE_COMMIT header should not be configured on any exchange because autoCommitEnable=true
        to.expectedHeaderValuesReceivedInAnyOrder(KafkaConstants.LAST_RECORD_BEFORE_COMMIT, null, null, null, null, null);

        for (int k = 0; k < 5; k++) {
            String msg = "message-" + k;
            ProducerRecord<String, String> data = new ProducerRecord<String, String>(TOPIC, "1", msg);
            producer.send(data);
        }

        to.assertIsSatisfied(3000);

        assertEquals(5, StreamSupport.stream(MockConsumerInterceptor.recordsCaptured.get(0).records(TOPIC).spliterator(), false).count());
    }

    @Test
    @Ignore("Currently there is a bug in kafka which leads to an uninterruptable thread so a resub take too long (works manually)")
    public void kafkaMessageIsConsumedByCamelSeekedToBeginning() throws Exception {
        to.expectedMessageCount(5);
        to.expectedBodiesReceivedInAnyOrder("message-0", "message-1", "message-2", "message-3", "message-4");
        for (int k = 0; k < 5; k++) {
            String msg = "message-" + k;
            ProducerRecord<String, String> data = new ProducerRecord<String, String>(TOPIC, "1", msg);
            producer.send(data);
        }
        to.assertIsSatisfied(3000);

        to.reset();

        to.expectedMessageCount(5);
        to.expectedBodiesReceivedInAnyOrder("message-0", "message-1", "message-2", "message-3", "message-4");

        //Restart endpoint,
        context.stopRoute("foo");

        KafkaEndpoint kafkaEndpoint = (KafkaEndpoint) from;
        kafkaEndpoint.getConfiguration().setSeekTo("beginning");

        context.startRoute("foo");

        // As wee set seek to beginning we should re-consume all messages
        to.assertIsSatisfied(3000);
    }

    @Test
    @Ignore("Currently there is a bug in kafka which leads to an uninterruptable thread so a resub take too long (works manually)")
    public void kafkaMessageIsConsumedByCamelSeekedToEnd() throws Exception {
        to.expectedMessageCount(5);
        to.expectedBodiesReceivedInAnyOrder("message-0", "message-1", "message-2", "message-3", "message-4");
        for (int k = 0; k < 5; k++) {
            String msg = "message-" + k;
            ProducerRecord<String, String> data = new ProducerRecord<String, String>(TOPIC, "1", msg);
            producer.send(data);
        }
        to.assertIsSatisfied(3000);

        to.reset();

        to.expectedMessageCount(0);

        //Restart endpoint,
        context.stopRoute("foo");

        KafkaEndpoint kafkaEndpoint = (KafkaEndpoint) from;
        kafkaEndpoint.getConfiguration().setSeekTo("end");

        context.startRoute("foo");

        // As wee set seek to end we should not re-consume any messages
        synchronized (this) {
            Thread.sleep(1000);
        }
        to.assertIsSatisfied(3000);
    }
}

