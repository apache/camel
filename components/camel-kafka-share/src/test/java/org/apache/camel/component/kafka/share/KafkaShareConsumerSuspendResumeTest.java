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
package org.apache.camel.component.kafka.share;

import java.util.List;
import java.util.Properties;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import org.apache.camel.BindToRegistry;
import org.apache.camel.ServiceStatus;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.apache.kafka.clients.consumer.ShareConsumer;
import org.junit.jupiter.api.Test;

import static org.apache.camel.component.kafka.share.RecordingShareConsumer.record;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * The share consumer, which does not support suspension, is stopped when the CamelContext is suspended and must consume
 * again when the CamelContext is resumed. A consumer of a started CamelContext starts at once.
 */
class KafkaShareConsumerSuspendResumeTest extends CamelTestSupport {

    private static final String TOPIC = "orders";

    private final List<RecordingShareConsumer> consumers = new CopyOnWriteArrayList<>();

    @BindToRegistry("recordingFactory")
    private final KafkaShareClientFactory factory = new KafkaShareClientFactory() {
        @Override
        public ShareConsumer<Object, Object> getShareConsumer(Properties kafkaProps) {
            RecordingShareConsumer consumer = new RecordingShareConsumer();
            consumers.add(consumer);
            return consumer;
        }

        @Override
        public String getBrokers(KafkaShareConfiguration configuration) {
            return "localhost:9092";
        }
    };

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("kafka-share:" + TOPIC + "?brokers=localhost:9092&groupId=resume&pollTimeoutMs=100").routeId("share")
                        .to("mock:result");
            }
        };
    }

    @Test
    void consumesAgainAfterCamelContextIsResumed() throws Exception {
        RecordingShareConsumer first = subscribedConsumer(0);

        context.suspend();
        await().atMost(10, TimeUnit.SECONDS).until(first::isClosed);

        context.resume();
        assertThat(context.getRouteController().getRouteStatus("share")).isEqualTo(ServiceStatus.Started);

        MockEndpoint result = getMockEndpoint("mock:result");
        result.expectedBodiesReceived("after resume");

        subscribedConsumer(1).addRecord(record(TOPIC, 0, null, "after resume", (short) 1));

        result.assertIsSatisfied();
    }

    @Test
    void consumesWithAComponentAddedAfterCamelContextIsStarted() throws Exception {
        subscribedConsumer(0);

        // a component added to a started CamelContext is not notified that the CamelContext started
        KafkaShareComponent component = new KafkaShareComponent();
        component.setKafkaShareClientFactory(factory);
        context.addComponent("kafka-share-late", component);
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("kafka-share-late:" + TOPIC + "?brokers=localhost:9092&groupId=late&pollTimeoutMs=100")
                        .routeId("late").to("mock:late");
            }
        });

        MockEndpoint late = getMockEndpoint("mock:late");
        late.expectedBodiesReceived("late record");

        subscribedConsumer(1).addRecord(record(TOPIC, 0, null, "late record", (short) 1));

        late.assertIsSatisfied();
    }

    private RecordingShareConsumer subscribedConsumer(int index) {
        await().atMost(10, TimeUnit.SECONDS)
                .until(() -> consumers.size() > index && consumers.get(index).subscription().contains(TOPIC));
        return consumers.get(index);
    }
}
