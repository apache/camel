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
package org.apache.camel.component.google.storage.unit;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.BucketInfo;
import com.google.cloud.storage.Storage;
import org.apache.camel.CamelContext;
import org.apache.camel.Consumer;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.google.storage.GoogleCloudStorageComponent;
import org.apache.camel.component.google.storage.localstorage.LocalStorageHelper;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spi.ExceptionHandler;
import org.apache.camel.spi.PollingConsumerPollStrategy;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A listed object that cannot be got (access denied, an object encrypted with a key the consumer cannot use) must not
 * stop the consumer: the other listed objects are consumed, and the poll fails only when none of the listed objects
 * could be got. The consumer lists the whole bucket at every poll, so before this one such object stopped the consumer
 * from consuming any object of the bucket.
 */
class ConsumerObjectThatCannotBeGotTest extends CamelTestSupport {

    private static final String BUCKET = "bkt";

    private Storage storage;
    private final CountDownLatch failedPolls = new CountDownLatch(2);
    private final CountDownLatch skippedReported = new CountDownLatch(1);
    private final List<String> handled = new CopyOnWriteArrayList<>();

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        storage = LocalStorageHelper.getOptions().getService();
        storage.create(BucketInfo.of(BUCKET));
        context.getComponent("google-storage", GoogleCloudStorageComponent.class).getConfiguration()
                .setStorageClient(storage);
        context.getRegistry().bind("countPolls", new PollingConsumerPollStrategy() {
            @Override
            public boolean begin(Consumer consumer, Endpoint endpoint) {
                return true;
            }

            @Override
            public void commit(Consumer consumer, Endpoint endpoint, int polledMessages) {
                // noop
            }

            @Override
            public boolean rollback(Consumer consumer, Endpoint endpoint, int retryCounter, Exception e) {
                failedPolls.countDown();
                return false;
            }
        });
        context.getRegistry().bind("recordErrors", new ExceptionHandler() {
            @Override
            public void handleException(Throwable exception) {
                handled.add(String.valueOf(exception.getMessage()));
            }

            @Override
            public void handleException(String message, Throwable exception) {
                record(message);
            }

            @Override
            public void handleException(String message, Exchange exchange, Throwable exception) {
                record(message);
            }

            private void record(String message) {
                handled.add(message);
                if (message.contains("b.txt")) {
                    skippedReported.countDown();
                }
            }
        });
        return context;
    }

    @Test
    void objectThatCannotBeGotDoesNotBlockTheOtherObjects() throws Exception {
        put("a.txt");
        put("b.txt");
        put("c.txt");
        LocalStorageHelper.refuseReads(BUCKET, "b.txt");
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceivedInAnyOrder("a.txt", "c.txt");

        addConsumerRoute("&exceptionHandler=#recordErrors");

        mock.assertIsSatisfied();
        assertTrue(skippedReported.await(20, TimeUnit.SECONDS), "the skipped object is reported: " + handled);
        assertNotNull(storage.get(BlobId.of(BUCKET, "b.txt")), "b.txt stays");
    }

    @Test
    void pollFailsWhenNoListedObjectCanBeGot() throws Exception {
        put("a.txt");
        put("b.txt");
        LocalStorageHelper.refuseReads(BUCKET, "a.txt");
        LocalStorageHelper.refuseReads(BUCKET, "b.txt");
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(0);

        addConsumerRoute("&pollStrategy=#countPolls");

        // as for a failed listing (expired credentials, no permission to get objects): the poll fails, so the error
        // handling and the backoff of the scheduled consumer apply
        assertTrue(failedPolls.await(20, TimeUnit.SECONDS), "the polls fail");
        mock.assertIsSatisfied();
        assertNull(skippedReportedMessage(), "no object is reported as skipped: " + handled);
    }

    private String skippedReportedMessage() {
        return handled.stream().filter(m -> m.contains("skipped")).findFirst().orElse(null);
    }

    private void put(String name) {
        storage.create(BlobInfo.newBuilder(BUCKET, name).build(), name.getBytes(StandardCharsets.UTF_8));
    }

    private void addConsumerRoute(String options) throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("google-storage://" + BUCKET + "?initialDelay=0&delay=10" + options)
                        .convertBodyTo(String.class)
                        .to("mock:result");
            }
        });
    }
}
