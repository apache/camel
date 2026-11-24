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
package org.apache.camel.component.rocketmq.reply;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.TimeoutMap;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.support.service.ServiceHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * Every request that the reply manager registers completes: also a request whose send succeeds while, or after, the
 * reply manager stops (the producer registers the reply in the callback of the send).
 */
class RocketMQReplyManagerStopTest {

    private final Map<String, AtomicInteger> completions = new ConcurrentHashMap<>();
    private CamelContext context;
    private ScheduledExecutorService executorService;
    private TestReplyManager replyManager;

    @BeforeEach
    void setUp() {
        context = new DefaultCamelContext();
        context.start();
        executorService = Executors.newSingleThreadScheduledExecutor();
        replyManager = new TestReplyManager(context, executorService);
        ServiceHelper.startService(replyManager);
    }

    @AfterEach
    void tearDown() {
        ServiceHelper.stopService(replyManager);
        executorService.shutdownNow();
        context.stop();
    }

    @Test
    void testRequestWaitingWhenTheReplyManagerStopsIsCompleted() {
        // control (passes without the change): the stop completes the requests that wait for their reply
        Exchange exchange = register("camel-rocketmq-key-1");

        ServiceHelper.stopService(replyManager);

        assertCompletedOnce("camel-rocketmq-key-1", exchange);
    }

    @Test
    void testRequestRegisteredAfterTheStopIsCompleted() {
        ServiceHelper.stopService(replyManager);

        // the send of the request succeeded after the stop
        Exchange exchange = register("camel-rocketmq-key-1");

        assertCompletedOnce("camel-rocketmq-key-1", exchange);
    }

    @Test
    void testRequestRegisteredWhileTheStopDrainsIsCompleted() {
        Exchange first = register("camel-rocketmq-key-1");
        Exchange[] second = new Exchange[1];
        // the send of the second request succeeds while the stop completes the first request (after the drain took the
        // entries out of the map, and before the map is cleared)
        replyManager.timeoutMap.addListener((type, key, value) -> {
            if (type == TimeoutMap.Listener.Type.Evict && "camel-rocketmq-key-1".equals(key)) {
                second[0] = register("camel-rocketmq-key-2");
            }
        });

        ServiceHelper.stopService(replyManager);

        assertCompletedOnce("camel-rocketmq-key-1", first);
        assertCompletedOnce("camel-rocketmq-key-2", second[0]);
    }

    private Exchange register(String key) {
        Exchange exchange = new DefaultExchange(context);
        AtomicInteger count = completions.computeIfAbsent(key, k -> new AtomicInteger());
        replyManager.registerReply(replyManager, exchange, doneSync -> count.incrementAndGet(), key, 60000);
        return exchange;
    }

    private void assertCompletedOnce(String key, Exchange exchange) {
        assertEquals(1, completions.get(key).get(), "The exchange of " + key + " should be completed once");
        assertInstanceOf(RejectedExecutionException.class, exchange.getException());
    }

    private static final class TestReplyManager extends RocketMQReplyManagerSupport {

        TestReplyManager(CamelContext camelContext, ScheduledExecutorService executorService) {
            super(camelContext);
            setScheduledExecutorService(executorService);
        }

        @Override
        protected void doStart() throws Exception {
            // no consumer of the reply topic (it needs a broker): the requests do not get a reply in this test, and the
            // purge task does not run during the test (the requests do not time out)
            timeoutMap = new ReplyTimeoutMap(executorService, 3600000);
            ServiceHelper.startService(timeoutMap);
        }
    }
}
