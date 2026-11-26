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
package org.apache.camel.component.tahu;

import java.util.Date;
import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelContext;
import org.apache.camel.Consumer;
import org.apache.camel.Endpoint;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.service.ServiceHelper;
import org.eclipse.tahu.message.SparkplugBPayloadEncoder;
import org.eclipse.tahu.message.model.Metric.MetricBuilder;
import org.eclipse.tahu.message.model.MetricDataType;
import org.eclipse.tahu.message.model.SparkplugBPayload;
import org.eclipse.tahu.message.model.SparkplugMeta;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A tahu-host consumer created again for the same host id (the route is restarted, reloaded or removed and added again)
 * gets the Sparkplug messages through its own host application, and building a host application for each consumer
 * leaves one SequenceMonitorTimer thread of Tahu running.
 */
class TahuHostConsumerHostApplicationTest {

    private static final String SPARKPLUG_SUBSCRIPTION = "spBv1.0/#";

    @Test
    void routeAddedAgainGetsTheMessages() throws Exception {
        try (FakeMqttBroker broker = new FakeMqttBroker(); CamelContext context = new DefaultCamelContext()) {
            String from = "tahu-host:AgainHost" + broker.getPort() + "?clientId=AgainHostClient&servers=Fake:tcp://localhost:"
                          + broker.getPort();
            context.addRoutes(route(from, "mock:a"));
            context.start();
            await().atMost(20, TimeUnit.SECONDS).until(() -> sparkplugSubscriptions(broker) == 1);

            // the route is removed and added again with the same endpoint: a new consumer is created
            context.getRouteController().stopRoute("host");
            context.removeRoute("host");
            context.addRoutes(route(from, "mock:b"));
            await().atMost(20, TimeUnit.SECONDS).until(() -> sparkplugSubscriptions(broker) >= 2);

            MockEndpoint b = context.getEndpoint("mock:b", MockEndpoint.class);
            b.expectedMessageCount(1);
            b.expectedHeaderReceived(TahuConstants.MESSAGE_TYPE, "NBIRTH");
            // wait a while after the message for one to the old route too
            b.setAssertPeriod(500);
            broker.publish("spBv1.0/AgainGroup/NBIRTH/AgainNode", nbirth());

            // without the fix the new consumer took the host application of the old one from a static map: Tahu gave
            // the message to the old consumer (or rejected it, as that host application was shut down)
            MockEndpoint.assertIsSatisfied(context, 20, TimeUnit.SECONDS);
            assertEquals(0, context.getEndpoint("mock:a", MockEndpoint.class).getReceivedCounter());
        }
    }

    @Test
    void oneSequenceMonitorTimerThread() throws Exception {
        try (CamelContext context = new DefaultCamelContext()) {
            context.start();
            Endpoint endpoint
                    = context.getEndpoint("tahu-host:TimerHost?clientId=TimerHostClient&servers=Mqtt:tcp://localhost:1883");
            for (int i = 0; i < 5; i++) {
                // a consumer builds its host application when it is created (initialized only: starting would connect)
                Consumer consumer = endpoint.createConsumer(exchange -> {
                });
                ServiceHelper.initService(consumer);
            }

            // without the fix every host application started a timer thread of its own, and none of them was stopped
            // (a timer that is cancelled ends its thread right after)
            await().atMost(20, TimeUnit.SECONDS).until(() -> sequenceMonitorTimerThreads() == 1);
        }
    }

    private static RouteBuilder route(String from, String to) {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from(from).routeId("host")
                        // the exchanges of the messages, not those of each metric
                        .filter(header(TahuConstants.MESSAGE_TYPE).isNotNull())
                        .to(to);
            }
        };
    }

    private static long sparkplugSubscriptions(FakeMqttBroker broker) {
        return broker.getSubscriptions().stream().filter(SPARKPLUG_SUBSCRIPTION::equals).count();
    }

    private static long sequenceMonitorTimerThreads() {
        return Thread.getAllStackTraces().keySet().stream()
                .filter(t -> t.isAlive() && "SequenceMonitorTimer".equals(t.getName())).count();
    }

    private static byte[] nbirth() throws Exception {
        SparkplugBPayload payload = new SparkplugBPayload.SparkplugBPayloadBuilder().setTimestamp(new Date()).setSeq(0L)
                .addMetric(new MetricBuilder(SparkplugMeta.SPARKPLUG_BD_SEQUENCE_NUMBER_KEY, MetricDataType.Int64, 0L)
                        .createMetric())
                .createPayload();
        return new SparkplugBPayloadEncoder().getBytes(payload, false);
    }
}
