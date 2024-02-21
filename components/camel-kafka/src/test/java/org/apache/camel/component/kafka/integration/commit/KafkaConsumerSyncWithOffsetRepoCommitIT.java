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

import org.apache.camel.BindToRegistry;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.kafka.KafkaConstants;
import org.apache.camel.component.kafka.consumer.KafkaManualCommit;
import org.apache.camel.component.kafka.integration.common.KafkaTestUtil;
import org.apache.camel.support.processor.state.MemoryStateRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class KafkaConsumerSyncWithOffsetRepoCommitIT extends BaseManualCommitTestSupport {

    public static final String TOPIC = "testManualCommitSyncWithOffsetRepoTest";

    @BindToRegistry("stateRepository")
    private static final MemoryStateRepository stateRepository = new MemoryStateRepository();

    @AfterEach
    public void after() {
        cleanupKafka(TOPIC);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {

            @Override
            public void configure() {
                final String from = "kafka:" + TOPIC
                                    + "?groupId=KafkaConsumerSyncCommitIT&pollTimeoutMs=1000&autoCommitEnable=false&offsetRepository=#bean:stateRepository"
                                    + "&allowManualCommit=true&autoOffsetReset=earliest&kafkaManualCommitFactory=#class:org.apache.camel.component.kafka.consumer.DefaultKafkaManualCommitFactory";
                from(from).routeId("foo")
                        .to(KafkaTestUtil.MOCK_RESULT).process(e -> {
                            KafkaManualCommit manual
                                    = e.getIn().getHeader(KafkaConstants.MANUAL_COMMIT, KafkaManualCommit.class);
                            assertNotNull(manual);
                            manual.commit();
                        });
                from(from)
                        .routeId("bar").autoStartup(false).to(KafkaTestUtil.MOCK_RESULT_BAR);
            }
        };
    }

    @DisplayName("Tests that the offset repository gets updated when using in conjunction with the Sync commit manager")
    @Test
    public void kafkaManualCommitWithOffsetRepo() throws Exception {
        kafkaManualCommitTestWithStateRepository(TOPIC, stateRepository);
    }

}
