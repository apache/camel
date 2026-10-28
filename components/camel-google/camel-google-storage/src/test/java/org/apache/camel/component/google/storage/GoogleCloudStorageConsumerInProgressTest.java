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

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.BucketInfo;
import com.google.cloud.storage.Storage;
import org.apache.camel.CamelContext;
import org.apache.camel.Consumer;
import org.apache.camel.Endpoint;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.google.storage.localstorage.LocalStorageHelper;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spi.PollingConsumerPollStrategy;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.apache.camel.util.CastUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * An object whose exchange is still being routed asynchronously must not be consumed again by the next poll, and an
 * object whose exchange a stopping consumer did not process is consumed again.
 */
class GoogleCloudStorageConsumerInProgressTest extends CamelTestSupport {

    private final AtomicInteger consumed = new AtomicInteger();
    private final CountDownLatch inRoute = new CountDownLatch(1);
    private final CountDownLatch twoPolls = new CountDownLatch(2);
    private final CountDownLatch release = new CountDownLatch(1);

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        Storage storage = LocalStorageHelper.getOptions().getService();
        storage.create(BucketInfo.of("myCamelBucket"));
        storage.create(BlobInfo.newBuilder("myCamelBucket", "a.txt").build(), "hello".getBytes(StandardCharsets.UTF_8));
        context.getComponent("google-storage", GoogleCloudStorageComponent.class).getConfiguration()
                .setStorageClient(storage);
        // counts the polls that completed (the exchanges of the poll were handed to the route)
        context.getRegistry().bind("countPolls", new PollingConsumerPollStrategy() {
            @Override
            public boolean begin(Consumer consumer, Endpoint endpoint) {
                return true;
            }

            @Override
            public void commit(Consumer consumer, Endpoint endpoint, int polledMessages) {
                twoPolls.countDown();
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
                // seda hands the on completions (the delete) over to this route
                from("seda:work")
                        .process(e -> {
                            inRoute.countDown();
                            release.await(20, TimeUnit.SECONDS);
                        })
                        .to("mock:result");
            }
        };
    }

    @Test
    void objectInFlightIsNotConsumedAgain() throws Exception {
        assertNotConsumedAgain("");
    }

    @Test
    void objectInFlightIsNotConsumedAgainWithObjectName() throws Exception {
        assertNotConsumedAgain("&objectName=a.txt");
    }

    @Test
    void notProcessedObjectIsConsumedAgain() throws Exception {
        GoogleCloudStorageEndpoint endpoint
                = context.getEndpoint("google-storage://myCamelBucket", GoogleCloudStorageEndpoint.class);
        // never started, so the consumer does not process any exchange of the batch
        GoogleCloudStorageConsumer consumer = (GoogleCloudStorageConsumer) endpoint.createConsumer(exchange -> {
        });
        List<Blob> blobs = List.of(endpoint.getStorageClient().get("myCamelBucket", "a.txt"));

        consumer.processBatch(CastUtils.cast(consumer.createExchanges(blobs)));

        assertThat(consumer.createExchanges(blobs))
                .as("a.txt stayed in progress although its exchange was not processed")
                .hasSize(1);
    }

    private void assertNotConsumedAgain(String options) throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("hello");

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("google-storage://myCamelBucket?delay=10&pollStrategy=#countPolls" + options)
                        .process(e -> consumed.incrementAndGet())
                        .to("seda:work");
            }
        });

        assertThat(inRoute.await(20, TimeUnit.SECONDS)).as("the object was not consumed").isTrue();
        // the second poll has completed while the first exchange is still held in the seda route
        assertThat(twoPolls.await(20, TimeUnit.SECONDS)).as("the consumer did not poll again").isTrue();
        int consumedWhileInFlight = consumed.get();
        release.countDown();

        assertThat(consumedWhileInFlight)
                .as("a.txt was consumed again by a later poll while its first exchange was still in flight")
                .isEqualTo(1);
        mock.assertIsSatisfied();
    }
}
