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

import java.util.Properties;

import org.apache.camel.Endpoint;
import org.apache.camel.EndpointInject;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class KafkaConsumerBatchSizeTest extends BaseEmbeddedKafkaTest {

    public static final String TOPIC = "test";

    @EndpointInject(uri = "kafka:" + TOPIC
            + "?autoOffsetReset=earliest"
            + "&autoCommitEnable=false"
            + "&consumerStreams=10"
    )
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
                from(from).routeId("foo").to(to).setId("First");
            }
        };
    }

    @Test
    public void kafkaMessagesIsConsumedByCamel() throws Exception {

        //First 2 must not be committed since batch size is 3
        to.expectedBodiesReceivedInAnyOrder("m1", "m2");
        for (int k = 1; k <= 2; k++) {
            String msg = "m" + k;
            ProducerRecord<String, String> data = new ProducerRecord<String, String>(TOPIC, "1", msg);
            producer.send(data);
        }
        to.assertIsSatisfied();

        to.reset();

        to.expectedBodiesReceivedInAnyOrder("m3", "m4", "m5", "m6", "m7", "m8", "m9", "m10");

        //Restart endpoint,
        context.stopRoute("foo");
        context.startRoute("foo");

        //Second route must wake up and consume all from scratch and commit 9 consumed
        for (int k = 3; k <= 10; k++) {
            String msg = "m" + k;
            ProducerRecord<String, String> data = new ProducerRecord<String, String>(TOPIC, "1", msg);
            producer.send(data);
        }

        to.assertIsSatisfied();
    }
}

