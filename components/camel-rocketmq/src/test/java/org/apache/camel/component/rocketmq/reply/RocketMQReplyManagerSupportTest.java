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

import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangeTimedOutException;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.support.service.ServiceHelper;
import org.apache.rocketmq.common.message.MessageExt;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * The reply consumer, the request timeout and a duplicate of the reply may each try to complete an InOut exchange: only
 * one of them may complete it.
 */
public class RocketMQReplyManagerSupportTest {

    private static final String KEY = "camel-rocketmq-test-key";

    private final AtomicInteger completions = new AtomicInteger();
    private CamelContext context;
    private ScheduledExecutorService executorService;
    private TestReplyManager replyManager;
    private Exchange exchange;

    @BeforeEach
    public void setUp() {
        context = new DefaultCamelContext();
        context.start();
        executorService = Executors.newSingleThreadScheduledExecutor();
        replyManager = new TestReplyManager(context, executorService);
        ServiceHelper.startService(replyManager);
        exchange = new DefaultExchange(context);
        replyManager.registerReply(replyManager, exchange, doneSync -> completions.incrementAndGet(), KEY, 10000);
    }

    @AfterEach
    public void tearDown() {
        ServiceHelper.stopService(replyManager);
        executorService.shutdownNow();
        context.stop();
    }

    @Test
    public void testReply() {
        replyManager.handleReplyMessage(KEY, reply("Bye World"));

        assertEquals(1, completions.get());
        assertNull(exchange.getException());
        assertEquals("Bye World", exchange.getMessage().getBody(String.class));
    }

    @Test
    public void testTimeoutWhileReplyIsHandled() {
        // the request times out after the reply consumer found the handler of the reply
        replyManager.testTimeoutMap.beforeRemove = () -> replyManager.testTimeoutMap.timeout();

        replyManager.handleReplyMessage(KEY, reply("Bye World"));

        assertEquals(1, completions.get(), "The exchange should be completed once");
        assertInstanceOf(ExchangeTimedOutException.class, exchange.getException());
    }

    @Test
    public void testDuplicateReply() {
        // a duplicate of the reply is handled by another consumer thread at the same time
        replyManager.testTimeoutMap.beforeRemove = () -> replyManager.handleReplyMessage(KEY, reply("Bye World"));

        replyManager.handleReplyMessage(KEY, reply("Bye World"));

        assertEquals(1, completions.get(), "The exchange should be completed once");
        assertEquals("Bye World", exchange.getMessage().getBody(String.class));
    }

    private static MessageExt reply(String body) {
        MessageExt messageExt = new MessageExt();
        messageExt.setBody(body.getBytes(StandardCharsets.UTF_8));
        return messageExt;
    }

    private static final class TestReplyManager extends RocketMQReplyManagerSupport {

        private TestTimeoutMap testTimeoutMap;

        TestReplyManager(CamelContext camelContext, ScheduledExecutorService executorService) {
            super(camelContext);
            setScheduledExecutorService(executorService);
        }

        @Override
        protected void doStart() throws Exception {
            // no consumer of the reply topic (it needs a broker): the test hands the replies over itself
            testTimeoutMap = new TestTimeoutMap(executorService);
            timeoutMap = testTimeoutMap;
            ServiceHelper.startService(timeoutMap);
        }
    }

    private static final class TestTimeoutMap extends ReplyTimeoutMap {

        private final AtomicLong now = new AtomicLong(1000);
        private Runnable beforeRemove;

        TestTimeoutMap(ScheduledExecutorService executorService) {
            // the purge task does not run during the test (the test lets the request time out itself)
            super(executorService, 3600000);
        }

        @Override
        protected long currentTime() {
            return now.get();
        }

        @Override
        public ReplyHandler remove(String key) {
            Runnable task = beforeRemove;
            beforeRemove = null;
            if (task != null) {
                task.run();
            }
            return super.remove(key);
        }

        void timeout() {
            now.addAndGet(60000);
            purge();
        }
    }
}
