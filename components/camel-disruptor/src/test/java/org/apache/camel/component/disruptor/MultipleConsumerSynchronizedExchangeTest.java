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
package org.apache.camel.component.disruptor;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.support.SynchronizationAdapter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The consumers of a multipleConsumers endpoint finish at the same time, one with a failure: the exchange is completed
 * once, with the failure. On main the consumer that failed was held after it was counted, so the other consumer was
 * counted last and completed the exchange as a success first.
 */
public class MultipleConsumerSynchronizedExchangeTest {

    private final CamelContext context = new DefaultCamelContext();
    private final List<String> events = new CopyOnWriteArrayList<>();

    @AfterEach
    void stopContext() throws Exception {
        context.close();
    }

    @Test
    void testConsumersFinishTogetherOneFails() throws Exception {
        Exchange published = new DefaultExchange(context);
        published.getMessage().setBody("Hello");
        published.getExchangeExtension().addOnCompletion(new SynchronizationAdapter() {
            @Override
            public void onComplete(Exchange exchange) {
                events.add("complete");
            }

            @Override
            public void onFailure(Exchange exchange) {
                events.add("failure");
            }
        });
        MultipleConsumerSynchronizedExchange synchronizedExchange = new MultipleConsumerSynchronizedExchange(published, 2);

        Exchange failedResult = new DefaultExchange(context);
        failedResult.setException(new IllegalArgumentException("Forced"));
        // the failing consumer is held when consumed() looks at its exception for the first time, while the other
        // consumer, which succeeded, is done
        CountDownLatch held = new CountDownLatch(1);
        CountDownLatch go = new CountDownLatch(1);
        AtomicBoolean first = new AtomicBoolean(true);
        Exchange heldResult = (Exchange) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] { Exchange.class },
                (proxy, method, args) -> {
                    if ("getException".equals(method.getName()) && method.getParameterCount() == 0
                            && first.getAndSet(false)) {
                        held.countDown();
                        go.await(10, TimeUnit.SECONDS);
                    }
                    try {
                        return method.invoke(failedResult, args);
                    } catch (InvocationTargetException e) {
                        throw e.getCause();
                    }
                });

        Thread consumer = new Thread(() -> synchronizedExchange.consumed(heldResult), "failing-consumer");
        consumer.start();
        assertTrue(held.await(10, TimeUnit.SECONDS));
        synchronizedExchange.consumed(new DefaultExchange(context));
        go.countDown();
        consumer.join(10000);
        assertFalse(consumer.isAlive());

        assertEquals(List.of("failure"), events);
        assertTrue(published.isFailed());
    }
}
