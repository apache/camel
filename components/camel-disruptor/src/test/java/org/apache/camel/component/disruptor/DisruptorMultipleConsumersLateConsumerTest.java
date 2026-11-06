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

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.camel.builder.NotifyBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * With multipleConsumers every consumer routes its own copy of the exchange, also a consumer that gets to the exchange
 * after another consumer has failed it.
 */
public class DisruptorMultipleConsumersLateConsumerTest extends CamelTestSupport {

    private final CountDownLatch blockerStarted = new CountDownLatch(1);
    private final CountDownLatch release = new CountDownLatch(1);

    @AfterEach
    void releaseConsumers() {
        // do not leave a consumer waiting when a test fails
        release.countDown();
    }

    @Test
    void testLateConsumerRoutesTheExchangeThatAnotherConsumerFailed() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:late");
        mock.expectedBodiesReceived("blocker", "fail");

        NotifyBuilder failingDone = new NotifyBuilder(context).from("disruptor:late*").whenDone(2).create();

        // the late consumer is busy with this one, so it gets to the next exchange only when it is released
        template.sendBody("direct:start", "blocker");
        assertTrue(blockerStarted.await(10, TimeUnit.SECONDS), "The late consumer should be busy");

        // the other consumer is done with both exchanges, and has failed the second one
        template.sendBody("direct:start", "fail");
        assertTrue(failingDone.matches(10, TimeUnit.SECONDS), "The failing consumer should be done");

        release.countDown();
        MockEndpoint.assertIsSatisfied(context);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").to("disruptor:late?multipleConsumers=true");

                from("disruptor:late?multipleConsumers=true").errorHandler(noErrorHandler())
                        .filter(body().isEqualTo("fail"))
                        .throwException(new IllegalArgumentException("Forced"));

                from("disruptor:late?multipleConsumers=true").errorHandler(noErrorHandler())
                        .filter(body().isEqualTo("blocker"))
                        .process(e -> {
                            blockerStarted.countDown();
                            release.await(20, TimeUnit.SECONDS);
                        })
                        .end()
                        .to("mock:late");
            }
        };
    }
}
