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
package org.apache.camel.component.resilience4j;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.camel.Exchange;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test that a timed-out worker thread does not write its late results back to the original exchange in async mode.
 */
public class ResilienceAsyncTimeoutWriteBackRaceTest extends CamelTestSupport {

    private final CountDownLatch workerDone = new CountDownLatch(1);

    @Test
    public void testTimeoutWorkerDoesNotOverwriteFallback() throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived("Fallback result");

        template.sendBody("direct:start", "Hello");

        MockEndpoint.assertIsSatisfied(context);

        assertTrue(workerDone.await(10, TimeUnit.SECONDS), "Worker thread should complete");

        Exchange received = getMockEndpoint("mock:result").getReceivedExchanges().get(0);
        assertEquals("Fallback result", received.getIn().getBody(String.class));
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .circuitBreaker()
                            .resilience4jConfiguration()
                                .asynchronous(true)
                                .timeoutEnabled(true)
                                .timeoutDuration(500)
                            .end()
                            .process(exchange -> {
                                try {
                                    Thread.sleep(3000);
                                exchange.getIn().setBody("Worker result");
                            } finally {
                                workerDone.countDown();
                            }
                        })
                        .onFallback()
                        .setBody(constant("Fallback result"))
                        .end()
                        .to("mock:result");
            }
        };
    }
}
