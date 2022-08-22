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

import org.apache.camel.Endpoint;
import org.apache.camel.EndpointInject;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.kafka.KafkaConstants;
import org.apache.camel.component.kafka.consumer.KafkaManualCommit;
import org.apache.camel.component.kafka.integration.BaseManualCommitTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.RepeatedTest;

import static org.junit.jupiter.api.Assertions.assertNotNull;

public class KafkaConsumerAsyncCommitIT extends BaseManualCommitTestSupport {

    public static final String TOPIC = "testManualAsyncCommitTest";

    @EndpointInject("kafka:" + TOPIC
                    + "?groupId=KafkaConsumerAsyncCommitIT&pollTimeoutMs=1000&autoCommitEnable=false"
                    + "&allowManualCommit=true&autoOffsetReset=earliest&kafkaManualCommitFactory=#class:org.apache.camel.component.kafka.consumer.DefaultKafkaManualAsyncCommitFactory")
    private Endpoint from;

    @AfterEach
    public void after() {
        cleanupKafka(TOPIC);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {

            @Override
            public void configure() {
                from(from).routeId("foo").to(to).process(e -> {
                    KafkaManualCommit manual = e.getIn().getHeader(KafkaConstants.MANUAL_COMMIT, KafkaManualCommit.class);
                    assertNotNull(manual);
                    manual.commit();
                });
                from(from).routeId("bar").autoStartup(false).to(toBar);
            }
        };
    }

    @RepeatedTest(1)
    public void kafkaManualCommit() throws Exception {
        kafkaManualCommitTest(TOPIC);
    }

}
