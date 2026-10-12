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
package org.apache.camel.component.google.storage;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.BucketInfo;
import com.google.cloud.storage.Storage;
import org.apache.camel.CamelContext;
import org.apache.camel.Consumer;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.google.storage.localstorage.LocalStorageHelper;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spi.CamelEvent;
import org.apache.camel.spi.CamelEvent.ExchangeCompletedEvent;
import org.apache.camel.spi.PollingConsumerPollStrategy;
import org.apache.camel.support.EventNotifierSupport;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * An object that is in flight when a poll lists (or gets) it, and whose exchange completes (the object is deleted and
 * no longer in progress) before the poll looks at the listed object, must not be consumed again from the stale listing.
 * With includeBody=false nothing is downloaded, so there is no 404 either that would skip it.
 */
class GoogleCloudStorageConsumerListingWindowTest extends CamelTestSupport {

    private final AtomicInteger consumed = new AtomicInteger();
    private final CountDownLatch inRoute = new CountDownLatch(1);
    private final CountDownLatch release = new CountDownLatch(1);
    private final CountDownLatch completed = new CountDownLatch(1);
    private final CountDownLatch windowPollDone = new CountDownLatch(1);
    // runs once, after the next listing (or get) has read the bucket and before the consumer receives the result
    private final AtomicReference<Runnable> afterNextRead = new AtomicReference<>();
    private volatile boolean windowOpened;
    private volatile String heldExchangeId;
    private Storage delegate;

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        delegate = LocalStorageHelper.getOptions().getService();
        delegate.create(BucketInfo.of("myCamelBucket"));
        delegate.create(BlobInfo.newBuilder("myCamelBucket", "a.txt").build(), "hello".getBytes(StandardCharsets.UTF_8));
        Storage storage = (Storage) Proxy.newProxyInstance(Storage.class.getClassLoader(), new Class<?>[] { Storage.class },
                (proxy, method, args) -> {
                    Object result;
                    try {
                        result = method.invoke(delegate, args);
                    } catch (InvocationTargetException e) {
                        throw e.getCause();
                    }
                    boolean list = "list".equals(method.getName());
                    boolean getObject = "get".equals(method.getName()) && args.length > 1 && args[1] instanceof String;
                    if (list || getObject) {
                        Runnable hook = afterNextRead.getAndSet(null);
                        if (hook != null) {
                            hook.run();
                        }
                    }
                    return result;
                });
        context.getComponent("google-storage", GoogleCloudStorageComponent.class).getConfiguration()
                .setStorageClient(storage);
        // the completed event of an exchange is sent after all its on completions have run
        context.getManagementStrategy().addEventNotifier(new EventNotifierSupport() {
            @Override
            public void notify(CamelEvent event) {
                if (event instanceof ExchangeCompletedEvent done
                        && done.getExchange().getExchangeId().equals(heldExchangeId)) {
                    completed.countDown();
                }
            }
        });
        context.getRegistry().bind("countPolls", new PollingConsumerPollStrategy() {
            @Override
            public boolean begin(Consumer consumer, Endpoint endpoint) {
                return true;
            }

            @Override
            public void commit(Consumer consumer, Endpoint endpoint, int polledMessages) {
                if (windowOpened) {
                    windowPollDone.countDown();
                }
            }

            @Override
            public boolean rollback(Consumer consumer, Endpoint endpoint, int retryCounter, Exception e) {
                return false;
            }
        });
        return context;
    }

    @AfterEach
    void releaseRoute() {
        release.countDown();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                // seda hands the on completions of the consumer (the delete, then the release of the object name) over
                // to this route
                from("seda:work")
                        .process(e -> {
                            heldExchangeId = e.getExchangeId();
                            inRoute.countDown();
                            release.await(20, TimeUnit.SECONDS);
                        })
                        .to("mock:result");
            }
        };
    }

    @Test
    void objectCompletedAfterTheListingIsNotConsumedAgain() throws Exception {
        assertNotConsumedFromStaleRead("");
    }

    @Test
    void objectCompletedAfterTheGetIsNotConsumedAgainWithObjectName() throws Exception {
        assertNotConsumedFromStaleRead("&objectName=a.txt");
    }

    @Test
    void exchangeOfAnObjectThatNoLongerExistsIsReleased() throws Exception {
        GoogleCloudStorageEndpoint endpoint
                = context.getEndpoint("google-storage://myCamelBucket", GoogleCloudStorageEndpoint.class);
        List<Exchange> released = new ArrayList<>();
        GoogleCloudStorageConsumer consumer = new GoogleCloudStorageConsumer(endpoint, exchange -> {
        }) {
            @Override
            public void releaseExchange(Exchange exchange, boolean autoRelease) {
                released.add(exchange);
                super.releaseExchange(exchange, autoRelease);
            }
        };
        Blob listed = delegate.get("myCamelBucket", "a.txt");
        delegate.delete("myCamelBucket", "a.txt");

        assertThat(consumer.createExchanges(List.of(listed))).isEmpty();

        assertThat(released).as("the exchange created for a.txt, whose download failed, was not released").hasSize(1);
    }

    private void assertNotConsumedFromStaleRead(String options) throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("google-storage://myCamelBucket?includeBody=false&delay=10&pollStrategy=#countPolls" + options)
                        .process(e -> consumed.incrementAndGet())
                        .to("seda:work");
            }
        });

        assertThat(inRoute.await(20, TimeUnit.SECONDS)).as("the object was not consumed").isTrue();
        AtomicReference<Boolean> completedInWindow = new AtomicReference<>();
        // the next poll reads a.txt while its exchange is in flight; before the consumer looks at what it read, the
        // exchange completes: a.txt is deleted and no longer in progress
        afterNextRead.set(() -> {
            release.countDown();
            try {
                completedInWindow.set(completed.await(20, TimeUnit.SECONDS));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            windowOpened = true;
        });

        assertThat(windowPollDone.await(20, TimeUnit.SECONDS)).as("the poll that read the stale a.txt did not end").isTrue();
        assertThat(completedInWindow.get()).as("the exchange of a.txt did not complete during the poll").isTrue();
        assertThat(delegate.get("myCamelBucket", "a.txt")).as("a.txt was not deleted").isNull();
        assertThat(consumed)
                .as("a.txt was consumed again from a listing made while its exchange was in flight")
                .hasValue(1);
        mock.assertIsSatisfied();
    }
}
