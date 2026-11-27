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

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import org.apache.camel.builder.NotifyBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An InOut producer gets the failure of a consumer only when all consumers are done, so a consumer that is still busy
 * with an earlier exchange routes the exchange as well, instead of ignoring it as if the producer had timed out.
 */
public class DisruptorMultipleConsumersInOutLateConsumerTest extends CamelTestSupport {

    private static final String URI = "disruptor:inOutLate?multipleConsumers=true";

    private final CountDownLatch blockerStarted = new CountDownLatch(1);
    private final CountDownLatch release = new CountDownLatch(1);

    @AfterEach
    void releaseConsumers() {
        // do not leave a consumer waiting when a test fails
        release.countDown();
    }

    @Test
    void testLateConsumerRoutesTheRequestThatAnotherConsumerFailed() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:late");
        mock.expectedBodiesReceived("blocker", "fail");

        NotifyBuilder failingDone = new NotifyBuilder(context).from("disruptor:inOutLate*").whenDone(2).create();

        // the late consumer is busy with this one, so it gets to the request only when it is released
        template.sendBody(URI, "blocker");
        assertTrue(blockerStarted.await(10, TimeUnit.SECONDS), "The late consumer should be busy");

        // the other consumer is done with both exchanges, and has failed the request
        CompletableFuture<Object> future = template.asyncRequestBody(URI, "fail");
        assertTrue(failingDone.matches(10, TimeUnit.SECONDS), "The failing consumer should be done");
        // the producer waits for the late consumer as well
        assertFalse(future.isDone(), "The producer should still wait for the late consumer");

        release.countDown();

        ExecutionException e = assertThrows(ExecutionException.class, () -> future.get(20, TimeUnit.SECONDS));
        IllegalArgumentException cause = findCause(e, IllegalArgumentException.class);
        assertNotNull(cause, "The failure of the consumer should be returned: " + e);
        assertEquals("Forced", cause.getMessage());

        MockEndpoint.assertIsSatisfied(context);
    }

    private static <T extends Throwable> T findCause(Throwable t, Class<T> type) {
        for (Throwable cause = t; cause != null; cause = cause.getCause()) {
            if (type.isInstance(cause)) {
                return type.cast(cause);
            }
        }
        return null;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from(URI).errorHandler(noErrorHandler())
                        .filter(body().isEqualTo("fail"))
                        .throwException(new IllegalArgumentException("Forced"));

                from(URI).errorHandler(noErrorHandler())
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
