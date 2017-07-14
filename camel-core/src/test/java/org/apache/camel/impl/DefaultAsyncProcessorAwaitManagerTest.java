/**
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
package org.apache.camel.impl;

import java.util.LinkedList;
import java.util.concurrent.CountDownLatch;

import org.apache.camel.Exchange;
import org.apache.camel.MessageHistory;
import org.apache.camel.NamedNode;
import org.apache.camel.spi.AsyncProcessorAwaitManager;
import org.apache.camel.spi.MessageHistoryFactory;
import org.junit.Test;

import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNull.nullValue;
import static org.junit.Assert.assertThat;

public class DefaultAsyncProcessorAwaitManagerTest {

    private static final MessageHistoryFactory MESSAGE_HISTORY_FACTORY = new DefaultMessageHistoryFactory();
    private DefaultAsyncProcessorAwaitManager defaultAsyncProcessorAwaitManager;
    private DefaultExchange exchange;
    private CountDownLatch latch;
    private Thread thread;

    @Test
    public void testNoMessageHistory() throws Exception {
        startAsyncProcess();
        AsyncProcessorAwaitManager.AwaitThread awaitThread = defaultAsyncProcessorAwaitManager.browse().iterator().next();
        assertThat(awaitThread.getRouteId(), is(nullValue()));
        assertThat(awaitThread.getNodeId(), is(nullValue()));
        waitForEndOfAsyncProcess();
    }

    @Test
    public void testMessageHistoryWithEmptyList() throws Exception {
        startAsyncProcess();
        exchange.setProperty(Exchange.MESSAGE_HISTORY, new LinkedList<MessageHistory>());
        AsyncProcessorAwaitManager.AwaitThread awaitThread = defaultAsyncProcessorAwaitManager.browse().iterator().next();
        assertThat(awaitThread.getRouteId(), is(nullValue()));
        assertThat(awaitThread.getNodeId(), is(nullValue()));
        waitForEndOfAsyncProcess();
    }

    @Test
    public void testMessageHistoryWithNullMessageHistory() throws Exception {
        startAsyncProcess();
        LinkedList<MessageHistory> messageHistories = new LinkedList<>();
        messageHistories.add(null);
        exchange.setProperty(Exchange.MESSAGE_HISTORY, messageHistories);
        AsyncProcessorAwaitManager.AwaitThread awaitThread = defaultAsyncProcessorAwaitManager.browse().iterator().next();
        assertThat(awaitThread.getRouteId(), is(nullValue()));
        assertThat(awaitThread.getNodeId(), is(nullValue()));
        waitForEndOfAsyncProcess();
    }

    @Test
    public void testMessageHistoryWithNullElements() throws Exception {
        startAsyncProcess();
        LinkedList<MessageHistory> messageHistories = new LinkedList<>();
        messageHistories.add(MESSAGE_HISTORY_FACTORY.newMessageHistory(null,
                new MockNamedNode().withId(null),
                0));
        exchange.setProperty(Exchange.MESSAGE_HISTORY, messageHistories);
        AsyncProcessorAwaitManager.AwaitThread awaitThread = defaultAsyncProcessorAwaitManager.browse().iterator().next();
        assertThat(awaitThread.getRouteId(), is(nullValue()));
        assertThat(awaitThread.getNodeId(), is(nullValue()));
        waitForEndOfAsyncProcess();
    }

    @Test
    public void testMessageHistoryWithNotNullElements() throws Exception {
        startAsyncProcess();
        LinkedList<MessageHistory> messageHistories = new LinkedList<>();
        messageHistories.add(MESSAGE_HISTORY_FACTORY.newMessageHistory("routeId",
                new MockNamedNode().withId("nodeId"),
                0));
        exchange.setProperty(Exchange.MESSAGE_HISTORY, messageHistories);
        AsyncProcessorAwaitManager.AwaitThread awaitThread = defaultAsyncProcessorAwaitManager.browse().iterator().next();
        assertThat(awaitThread.getRouteId(), is("routeId"));
        assertThat(awaitThread.getNodeId(), is("nodeId"));
        waitForEndOfAsyncProcess();
    }

    private void waitForEndOfAsyncProcess() {
        latch.countDown();
        while (thread.isAlive()) {
        }
    }

    private void startAsyncProcess() throws InterruptedException {
        defaultAsyncProcessorAwaitManager = new DefaultAsyncProcessorAwaitManager();
        latch = new CountDownLatch(1);
        BackgroundAwait backgroundAwait = new BackgroundAwait();
        exchange = new DefaultExchange(new DefaultCamelContext());
        thread = new Thread(backgroundAwait);
        thread.start();
        Thread.sleep(100);
    }


    private class BackgroundAwait implements Runnable {

        @Override
        public void run() {
            defaultAsyncProcessorAwaitManager.await(exchange, latch);
        }
    }

    private static class MockNamedNode implements NamedNode {

        private String id;

        @Override
        public String getId() {
            return id;
        }

        @Override
        public String getShortName() {
            return this.getClass().getSimpleName();
        }

        @Override
        public String getLabel() {
            return this.getClass().getName();
        }

        @Override
        public String getDescriptionText() {
            return this.getClass().getCanonicalName();
        }

        public MockNamedNode withId(String id) {
            this.id = id;
            return this;
        }
    }
}