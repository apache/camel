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
package org.apache.camel.component.minio;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelContext;
import org.apache.camel.Consumer;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spi.ExceptionHandler;
import org.apache.camel.spi.PollingConsumerPollStrategy;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A listed object that cannot be got (access denied, an object encrypted with a customer key the consumer does not
 * have) must not stop the consumer: the other listed objects are consumed, and the poll fails only when none of the
 * listed objects could be got. The bucket is an in-process fake of the S3 API, used through the real Minio client.
 */
class MinioConsumerObjectThatCannotBeGotTest extends CamelTestSupport {

    private FakeS3Server server;
    private final CountDownLatch failedPolls = new CountDownLatch(2);
    private final List<String> handled = new CopyOnWriteArrayList<>();
    private final CountDownLatch skippedReported = new CountDownLatch(1);

    @Override
    protected CamelContext createCamelContext() throws Exception {
        server = new FakeS3Server("bucket");
        server.start();
        CamelContext context = super.createCamelContext();
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
                handled.add(message);
                if (message.contains("b.txt")) {
                    skippedReported.countDown();
                }
            }

            @Override
            public void handleException(String message, Exchange exchange, Throwable exception) {
                handled.add(message);
                if (message.contains("b.txt")) {
                    skippedReported.countDown();
                }
            }
        });
        return context;
    }

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop();
        }
    }

    @Test
    void objectWhoseBodyCannotBeGotDoesNotBlockTheOtherObjects() throws Exception {
        server.put("a.txt", "a");
        server.put("b.txt", "b");
        server.put("c.txt", "c");
        server.refuseGets("b.txt");
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceivedInAnyOrder("a", "c");

        addConsumerRoute("&exceptionHandler=#recordErrors");

        mock.assertIsSatisfied();
        assertTrue(skippedReported.await(20, TimeUnit.SECONDS), "the skipped object is reported: " + handled);
        assertTrue(server.contains("b.txt"), "b.txt stays");
    }

    @Test
    void objectThatCannotBeStatedDoesNotBlockTheOtherObjects() throws Exception {
        server.put("a.txt", "a");
        server.put("b.txt", "b");
        server.put("c.txt", "c");
        server.refuseStats("b.txt");
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceivedInAnyOrder("a", "c");

        addConsumerRoute("&exceptionHandler=#recordErrors");

        mock.assertIsSatisfied();
        assertTrue(skippedReported.await(20, TimeUnit.SECONDS), "the skipped object is reported: " + handled);
        assertTrue(server.contains("b.txt"), "b.txt stays");
    }

    @Test
    void pollFailsWhenNoListedObjectCanBeGot() throws Exception {
        server.put("a.txt", "a");
        server.put("b.txt", "b");
        server.refuseGets("a.txt");
        server.refuseGets("b.txt");
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(0);

        addConsumerRoute("&pollStrategy=#countPolls");

        // as for a failed listing (expired credentials, no permission to get objects): the poll fails, so the error
        // handling and the backoff of the scheduled consumer apply
        assertTrue(failedPolls.await(20, TimeUnit.SECONDS), "the polls fail");
        mock.assertIsSatisfied();
    }

    private void addConsumerRoute(String options) throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("minio://bucket?accessKey=xxx&secretKey=yyy&region=us-east-1&initialDelay=0&delay=10"
                     + "&endpoint=" + server.endpoint() + options)
                        .convertBodyTo(String.class)
                        .to("mock:result");
            }
        });
    }
}
