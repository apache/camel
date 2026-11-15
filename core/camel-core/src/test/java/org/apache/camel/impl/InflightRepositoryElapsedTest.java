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
package org.apache.camel.impl;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.Collection;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.MessageHistory;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.InflightRepository;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CAMEL-26945: the time an inflight exchange has been at the node it is at, while it is still there.
 */
public class InflightRepositoryElapsedTest extends ContextTestSupport {

    private final CountDownLatch done = new CountDownLatch(1);

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        context.setMessageHistory(true);
        context.getInflightRepository().setInflightBrowseEnabled(true);
        return context;
    }

    @Test
    public void testElapsedAtTheNodeTheExchangeIsAt() {
        template.asyncSendBody("direct:start", "Hello World");
        try {
            // the exchange waits at the node: the time it has been there grows
            await().atMost(10, TimeUnit.SECONDS).until(() -> elapsed() >= 100);

            InflightRepository.InflightExchange inflight = context.getInflightRepository().browse().iterator().next();
            assertEquals("wait", inflight.getNodeId());
            long elapsed = inflight.getElapsed();
            long duration = inflight.getDuration();
            // and it is no longer than the exchange is old
            assertTrue(elapsed >= 100 && elapsed <= duration,
                    "Elapsed " + elapsed + " ms at the node of an exchange that is " + duration + " ms old");
        } finally {
            done.countDown();
        }
    }

    @Test
    public void testElapsedSinceCreatedOfAMessageHistoryWithoutAClock() {
        // a message history of another factory, which only has the time it was created
        long created = System.currentTimeMillis() - 250;
        MessageHistory history = (MessageHistory) Proxy.newProxyInstance(MessageHistory.class.getClassLoader(),
                new Class<?>[] { MessageHistory.class },
                (proxy, method, args) -> "getTime".equals(method.getName())
                        ? created : InvocationHandler.invokeDefault(proxy, method, args));

        long elapsed = history.getElapsedSinceCreated();
        assertTrue(elapsed >= 250 && elapsed < 10000, "Elapsed " + elapsed + " ms since created 250 ms ago");
    }

    private long elapsed() {
        Collection<InflightRepository.InflightExchange> list = context.getInflightRepository().browse();
        return list.isEmpty() ? 0 : list.iterator().next().getElapsed();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").routeId("slow")
                        .process(e -> done.await(10, TimeUnit.SECONDS)).id("wait");
            }
        };
    }
}
