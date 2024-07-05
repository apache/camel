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

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.kafka.KafkaConstants;
import org.apache.camel.component.kafka.consumer.KafkaManualCommit;
import org.apache.camel.component.kafka.consumer.errorhandler.KafkaConsumerListener;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.RepeatedTest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertNotNull;

public class KafkaConsumerPauseableEeiIT extends BaseManualCommitTestSupport {

    private static final Logger LOG = LoggerFactory.getLogger(KafkaConsumerPauseableEeiIT.class);

    public static final String TOPIC = "testPauseableEipTest";

    private volatile int count = 0;

    @AfterEach
    public void after() {
        cleanupKafka(TOPIC);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        String from = "kafka:" + TOPIC
                      + "?groupId=KafkaConsumerPauseableEeiIT&pollTimeoutMs=1000"
                      + "&autoOffsetReset=earliest&autoCommitEnable=false&allowManualCommit=true&maxPollRecords=1";

        return new RouteBuilder() {
            @Override
            public void configure() {
                from(from).routeId("foo")
                        .pausable(new KafkaConsumerListener(), o -> canContinue())
                        .log("IN ${body}")
                        .process(e -> {
                            log.info("Manual commit: " + e.getMessage().getBody(String.class));
                            KafkaManualCommit manual
                                    = e.getMessage().getHeader(KafkaConstants.MANUAL_COMMIT, KafkaManualCommit.class);
                            assertNotNull(manual);
                            manual.commit();
                        })
                        .log("RESULT ${body}")
                        .to("mock:result");
            }
        };
    }

    @RepeatedTest(1)
    public void kafkaPauseableEip() throws Exception {
        MockEndpoint result = contextExtension.getMockEndpoint("mock:result");

        result.expectedBodiesReceivedInAnyOrder("message-0", "message-1", "message-2", "message-10", "message-11", "message-12",
                "message-13");

        sendRecords(0, 15, TOPIC);

        result.assertIsSatisfied();
    }

    public boolean canContinue() {
        count++;
        boolean answer;
        if (count < 4 || count > 10) {
            answer = true;
        } else {
            answer = false;
        }
        LOG.info("canContinue count: {} -> {}", count, answer);
        return answer;
    }

}
