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
package org.apache.camel.component.file.remote.integration;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.camel.BindToRegistry;
import org.apache.camel.Consumer;
import org.apache.camel.Endpoint;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.file.GenericFileOperationFailedException;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spi.PollingConsumerPollStrategy;
import org.apache.camel.support.service.ServiceSupport;
import org.junit.jupiter.api.Test;

import static org.apache.camel.test.junit5.TestSupport.assertIsInstanceOf;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit test for login failure due bad password and no re connect attempts allowed
 */
public class FtpConsumerThrowExceptionOnLoginFailedIT extends FtpServerTestSupport {

    private final CountDownLatch latch = new CountDownLatch(1);

    @BindToRegistry("myPoll")
    private final MyPoll poll = new MyPoll();

    private String getFtpUrl() {
        return "ftp://dummy@localhost:{{ftp.server.port}}/badlogin?password=cantremember"
               + "&throwExceptionOnConnectFailed=true&maximumReconnectAttempts=0&pollStrategy=#myPoll&autoCreate=false";
    }

    @Test
    public void testBadLogin() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(0);

        assertTrue(latch.await(5, TimeUnit.SECONDS));

        MockEndpoint.assertIsSatisfied(context);

        // consumer should be stopped
        Consumer consumer = context.getRoute("foo").getConsumer();
        await().atMost(1, TimeUnit.SECONDS)
                .untilAsserted(() -> assertTrue(((ServiceSupport) consumer).isStopped(), "Consumer should be stopped"));
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from(getFtpUrl()).routeId("foo").to("mock:result");
            }
        };
    }

    private class MyPoll implements PollingConsumerPollStrategy {

        @Override
        public boolean begin(Consumer consumer, Endpoint endpoint) {
            return true;
        }

        @Override
        public void commit(Consumer consumer, Endpoint endpoint, int polledMessages) {
        }

        @Override
        public boolean rollback(Consumer consumer, Endpoint endpoint, int retryCounter, Exception cause) {
            GenericFileOperationFailedException e = assertIsInstanceOf(GenericFileOperationFailedException.class, cause);
            assertEquals(530, e.getCode());

            // stop the consumer
            consumer.stop();

            latch.countDown();

            return false;
        }
    }
}
