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

import java.util.Set;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.kafka.KafkaConstants;
import org.apache.camel.component.kafka.consumer.DefaultKafkaManualAsyncCommitFactory;
import org.apache.camel.component.kafka.consumer.KafkaManualCommit;
import org.apache.camel.component.kafka.integration.common.KafkaTestUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * With manual commits, a stop commits only the records that the route committed: the records that the route processed
 * but did not commit (as an aggregator holds the records of a group that is not complete) are consumed again after the
 * restart (CAMEL-26993).
 */
public class KafkaConsumerManualCommitStopIT extends BaseManualCommitTestSupport {

    public static final String TOPIC = "testManualCommitStopTest";

    private static final Set<String> COMMITTED = Set.of("message-0", "message-1", "message-2");

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
                     + "?groupId=KafkaConsumerManualCommitStopIT&pollTimeoutMs=1000&autoCommitEnable=false"
                     + "&allowManualCommit=true&autoOffsetReset=earliest&kafkaManualCommitFactory=#class:"
                     + DefaultKafkaManualAsyncCommitFactory.class.getName())
                        .routeId("foo")
                        .to(KafkaTestUtil.MOCK_RESULT)
                        // the route commits the first three records only
                        .filter(e -> COMMITTED.contains(e.getMessage().getBody(String.class)))
                        .process(e -> e.getMessage().getHeader(KafkaConstants.MANUAL_COMMIT, KafkaManualCommit.class)
                                .commit());
            }
        };
    }

    @Test
    public void testStopCommitsOnlyWhatTheRouteCommitted() throws Exception {
        to.expectedBodiesReceived("message-0", "message-1", "message-2", "message-3", "message-4");
        sendRecords(0, 5, TOPIC);
        to.assertIsSatisfied(10000);
        to.reset();

        contextExtension.getContext().getRouteController().stopRoute("foo");
        sendRecords(5, 8, TOPIC);

        // the records 3 and 4, which the route did not commit, are consumed again
        to.expectedBodiesReceived("message-3", "message-4", "message-5", "message-6", "message-7");
        contextExtension.getContext().getRouteController().startRoute("foo");
        to.assertIsSatisfied(30000);
    }
}
