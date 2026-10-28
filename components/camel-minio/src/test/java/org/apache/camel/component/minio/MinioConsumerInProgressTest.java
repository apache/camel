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

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelContext;
import org.apache.camel.Consumer;
import org.apache.camel.Endpoint;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spi.PollingConsumerPollStrategy;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An object whose exchange is still being routed asynchronously must not be consumed again by the next poll.
 */
class MinioConsumerInProgressTest extends CamelTestSupport {

    private final CountDownLatch inRoute = new CountDownLatch(1);
    private final CountDownLatch twoPolls = new CountDownLatch(2);
    private final CountDownLatch release = new CountDownLatch(1);
    private FakeS3Server s3;

    @Override
    protected CamelContext createCamelContext() throws Exception {
        s3 = new FakeS3Server("bkt");
        s3.put("a.txt", "hello");
        s3.start();
        CamelContext context = super.createCamelContext();
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
    void stopServer() {
        release.countDown();
        if (s3 != null) {
            s3.stop();
        }
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

    private void assertNotConsumedAgain(String options) throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("hello");

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("minio://bkt?accessKey=xxx&secretKey=yyy&region=us-east-1&delay=10&pollStrategy=#countPolls"
                     + options + "&endpoint=" + s3.endpoint())
                        .to("seda:work");
            }
        });

        assertTrue(inRoute.await(20, TimeUnit.SECONDS), "the object was not consumed");
        // the second poll has completed while the first exchange is still held in the seda route
        assertTrue(twoPolls.await(20, TimeUnit.SECONDS), "the consumer did not poll again");
        int gets = s3.gets("a.txt");
        release.countDown();

        mock.assertIsSatisfied();
        assertEquals(1, gets,
                "a.txt was consumed again by a later poll while its first exchange was still in flight");
    }
}
