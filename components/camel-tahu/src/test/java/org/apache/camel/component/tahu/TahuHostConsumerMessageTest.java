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
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.DefaultCamelContext;
import org.eclipse.tahu.message.SparkplugBPayloadEncoder;
import org.eclipse.tahu.message.model.Metric;
import org.eclipse.tahu.message.model.Metric.MetricBuilder;
import org.eclipse.tahu.message.model.MetricDataType;
import org.eclipse.tahu.message.model.SparkplugBPayload;
import org.eclipse.tahu.message.model.SparkplugMeta;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A Host Application consumer gives one exchange for each Sparkplug message.
 */
class TahuHostConsumerMessageTest {

    private static final String NODE = "spBv1.0/MessageGroup/%s/MessageNode";
    private static final String DEVICE = "spBv1.0/MessageGroup/%s/MessageNode/D1";

    @Test
    void oneExchangeForEachMessage() throws Exception {
        try (FakeMqttBroker broker = new FakeMqttBroker(); CamelContext context = new DefaultCamelContext()) {
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    // a host id of its own, as a consumer keeps its host application in a static map
                    from("tahu-host:MessageHost" + broker.getPort()
                         + "?clientId=MessageHostClient&servers=Fake:tcp://localhost:" + broker.getPort())
                            // the exchanges of the messages, not those of each metric
                            .filter(header(TahuConstants.MESSAGE_TYPE).isNotNull())
                            .to("mock:messages");
                }
            });
            context.start();
            MockEndpoint messages = context.getEndpoint("mock:messages", MockEndpoint.class);
            messages.expectedMessageCount(6);
            // wait a while after the last message for duplicates
            messages.setAssertPeriod(1000);

            await().atMost(20, TimeUnit.SECONDS).until(() -> broker.getSubscriptions().contains("spBv1.0/#"));
            broker.publish(NODE.formatted("NBIRTH"), payload(0, metric(SparkplugMeta.SPARKPLUG_BD_SEQUENCE_NUMBER_KEY,
                    MetricDataType.Int64, 0L), metric("temperature", MetricDataType.Double, 20.5)));
            broker.publish(NODE.formatted("NDATA"), payload(1, metric("temperature", MetricDataType.Double, 21.5)));
            broker.publish(DEVICE.formatted("DBIRTH"), payload(2, metric("level", MetricDataType.Int32, 1)));
            broker.publish(DEVICE.formatted("DDATA"), payload(3, metric("level", MetricDataType.Int32, 2)));
            broker.publish(DEVICE.formatted("DDEATH"), payload(4));
            broker.publish(NODE.formatted("NDEATH"), payload(null, metric(SparkplugMeta.SPARKPLUG_BD_SEQUENCE_NUMBER_KEY,
                    MetricDataType.Int64, 0L)));

            // without the fix each message came twice (an NDATA three times): from the callback of its type
            // (onNodeBirthArrived, ...) and from onMessage
            MockEndpoint.assertIsSatisfied(context, 20, TimeUnit.SECONDS);
            assertEquals(List.of("NBIRTH", "NDATA", "DBIRTH", "DDATA", "DDEATH", "NDEATH"),
                    messages.getReceivedExchanges().stream()
                            .map(e -> e.getMessage().getHeader(TahuConstants.MESSAGE_TYPE, String.class)).toList());
            // without the fix the metric headers replaced the other headers of a message that has metrics
            Exchange ddata = messages.getReceivedExchanges().get(3);
            assertEquals("MessageGroup/MessageNode/D1", ddata.getMessage().getHeader(TahuConstants.EDGE_NODE_DESCRIPTOR));
            assertEquals(3L, ddata.getMessage().getHeader(TahuConstants.MESSAGE_SEQUENCE_NUMBER));
            assertEquals(2, ddata.getMessage().getHeader(TahuConstants.METRIC_HEADER_PREFIX + "level", Metric.class)
                    .getValue());
        }
    }

    private static Metric metric(String name, MetricDataType type, Object value) throws Exception {
        return new MetricBuilder(name, type, value).createMetric();
    }

    private static byte[] payload(Integer seq, Metric... metrics) throws Exception {
        SparkplugBPayload.SparkplugBPayloadBuilder builder
                = new SparkplugBPayload.SparkplugBPayloadBuilder().setTimestamp(new Date());
        if (seq != null) {
            builder.setSeq(seq.longValue());
        }
        for (Metric metric : metrics) {
            builder.addMetric(metric);
        }
        return new SparkplugBPayloadEncoder().getBytes(builder.createPayload(), false);
    }
}
