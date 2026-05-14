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
package org.apache.camel.component.kafka.integration.batching;

import java.util.List;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.kafka.integration.common.KafkaTestUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Regression test for CAMEL-XXXXX: batchingIntervalMs timer not reset when a new accumulation cycle begins after an
 * idle period longer than batchingIntervalMs.
 *
 * After such an idle period, intervalWatch is already expired when the first new message arrives. When a subsequent
 * non-empty poll arrives with more messages, hasExpiredRecords() fires immediately due to the stale intervalWatch,
 * flushing only the first message as a single-message batch instead of accumulating it with the subsequent messages.
 *
 * The fix is to restart intervalWatch at the same point timeoutWatch is restarted — when exchangeList.isEmpty() is true
 * at the top of processExchange() in KafkaRecordBatchingProcessor.
 *
 * Test scenario: the first post-idle message is sent and flushed to Kafka alone, ensuring the consumer picks it up in a
 * dedicated poll. A 200ms gap (less than pollTimeoutMs=500ms) then separates it from the remaining messages, so the
 * subsequent poll carries those remaining messages while the first is still in the accumulation buffer.
 *
 * With the bug: intervalWatch is still expired from the idle period, so it fires on that second poll and prematurely
 * flushes only the first message — the remaining messages end up in a later batch.
 *
 * With the fix: intervalWatch is reset when the first message starts a new accumulation cycle, so the second poll does
 * not trigger a premature flush and all messages are accumulated into one batch.
 */
public class KafkaBatchingIntervalResetAfterIdleIT extends BatchingProcessingITSupport {

    public static final String TOPIC = "testBatchingIntervalResetAfterIdle";

    private static final int BATCHING_INTERVAL_MS = 2000;
    private static final int MAX_POLL_RECORDS = 5;

    @AfterEach
    public void after() {
        cleanupKafka(TOPIC);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        String from = "kafka:" + TOPIC
                      + "?groupId=KafkaBatchingIntervalResetAfterIdleIT"
                      + "&batching=true"
                      + "&maxPollRecords=" + MAX_POLL_RECORDS
                      + "&batchingIntervalMs=" + BATCHING_INTERVAL_MS
                      + "&pollTimeoutMs=500"
                      + "&autoOffsetReset=earliest";

        return new RouteBuilder() {
            @Override
            public void configure() {
                from(from).routeId("batching").to(KafkaTestUtil.MOCK_RESULT);
            }
        };
    }

    @Test
    public void intervalWatchShouldResetWhenNewAccumulationCycleBegins() throws Exception {
        // Baseline: produce a full batch and confirm it flushes as one.
        to.expectedMessageCount(1);
        sendRecords(0, MAX_POLL_RECORDS, TOPIC);
        to.assertIsSatisfied(5000);
        to.reset();

        // Idle for longer than batchingIntervalMs so that intervalWatch expires while the
        // queue is empty. This is the precondition that triggers the bug on the next cycle.
        Thread.sleep(BATCHING_INTERVAL_MS + 1000);

        // Send 1 message and flush it to Kafka before sending the rest. This guarantees the
        // consumer will poll it alone in a dedicated poll (Kafka consumer returns immediately
        // when records are available, so msg 1 is picked up before msgs 2-4 exist in Kafka).
        int postIdleCount = MAX_POLL_RECORDS - 1;
        sendRecords(MAX_POLL_RECORDS, MAX_POLL_RECORDS + 1, TOPIC);
        producer.flush();

        // 200ms gap: msg 1 will have been consumed in its own poll by the time msgs 2-4
        // arrive in Kafka. pollTimeoutMs=500ms means the consumer returns from any poll as
        // soon as a record is available, so msg 1 lands in a poll well within this window.
        Thread.sleep(200);

        // Send remaining messages. With the fix, the consumer will accumulate these with
        // msg 1 since intervalWatch was reset when msg 1 started the new cycle.
        // With the bug, intervalWatch is still expired and fires immediately on this poll,
        // flushing msg 1 alone before msgs 2-4 can be accumulated with it.
        sendRecords(MAX_POLL_RECORDS + 1, MAX_POLL_RECORDS + postIdleCount, TOPIC);
        producer.flush();

        // Wait for at least one batch. With the bug a batch of 1 arrives quickly;
        // with the fix a single batch of all 4 messages arrives after pollTimeoutMs.
        to.expectedMinimumMessageCount(1);
        to.assertIsSatisfied(8000);

        List<?> firstBatch = to.getExchanges().get(0).getMessage().getBody(List.class);
        assertEquals(postIdleCount, firstBatch.size(),
                "Expected all " + postIdleCount + " post-idle messages accumulated into one batch, "
                                                       + "but the first batch contained only " + firstBatch.size()
                                                       + " message(s) — intervalWatch was not reset when the new accumulation cycle began");
    }
}
