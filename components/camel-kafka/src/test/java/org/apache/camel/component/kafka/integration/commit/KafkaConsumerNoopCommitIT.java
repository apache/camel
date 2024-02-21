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
package org.apache.camel.component.kafka.integration.commit;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.kafka.KafkaConstants;
import org.apache.camel.component.kafka.consumer.KafkaManualCommit;
import org.apache.camel.component.kafka.integration.common.KafkaTestUtil;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

public class KafkaConsumerNoopCommitIT extends BaseManualCommitTestSupport {

    public static final String TOPIC = "testManualNoopCommitTest";

    @AfterEach
    public void after() {
        cleanupKafka(TOPIC);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {

            @Override
            public void configure() {
                from("kafka:" + TOPIC
                     + "?groupId=KafkaConsumerNoopCommitIT&pollTimeoutMs=1000&autoCommitEnable=false"
                     + "&allowManualCommit=true&autoOffsetReset=earliest&metadataMaxAgeMs=1000").routeId("foo")
                        .to(KafkaTestUtil.MOCK_RESULT).process(e -> {
                            KafkaManualCommit manual
                                    = e.getIn().getHeader(KafkaConstants.MANUAL_COMMIT, KafkaManualCommit.class);
                            assertNotNull(manual);
                            manual.commit();
                        });
                from("kafka:" + TOPIC
                     + "?groupId=KafkaConsumerNoopCommitIT&pollTimeoutMs=1000&autoCommitEnable=false"
                     + "&allowManualCommit=true&autoOffsetReset=earliest&metadataMaxAgeMs=1000").routeId("bar")
                        .autoStartup(false).to(KafkaTestUtil.MOCK_RESULT_BAR);
            }
        };
    }

    @Test
    public void kafkaAutoCommitDisabledDuringRebalance() throws Exception {
        to.expectedMessageCount(1);
        String firstMessage = "message-0";
        to.expectedBodiesReceivedInAnyOrder(firstMessage);

        ProducerRecord<String, String> data = new ProducerRecord<>(TOPIC, "1", firstMessage);
        producer.send(data);

        to.assertIsSatisfied(3000);

        to.reset();

        CamelContext context = contextExtension.getContext();
        context.getRouteController().stopRoute("foo");
        to.expectedMessageCount(0);

        String secondMessage = "message-1";
        data = new ProducerRecord<>(TOPIC, "1", secondMessage);
        producer.send(data);

        to.assertIsSatisfied(3000);

        to.reset();

        // start a new route in order to rebalance kafka
        context.getRouteController().startRoute("bar");
        toBar.expectedMessageCount(1);

        toBar.assertIsSatisfied();

        context.getRouteController().stopRoute("bar");

        // The route bar is not committing the offset, so by restarting foo, last 3 items will be processed
        context.getRouteController().startRoute("foo");
        to.expectedMessageCount(1);
        to.expectedBodiesReceivedInAnyOrder("message-1");

        to.assertIsSatisfied(3000);
    }

    @Test
    public void kafkaManualCommit() throws Exception {
        kafkaManualCommitTest(TOPIC);
    }

}
