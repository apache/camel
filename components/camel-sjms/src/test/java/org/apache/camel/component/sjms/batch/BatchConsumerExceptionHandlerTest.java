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
package org.apache.camel.component.sjms.batch;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.camel.BindToRegistry;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.sjms.support.JmsTestSupport;
import org.apache.camel.spi.ExceptionHandler;
import org.junit.jupiter.api.Test;

import static org.apache.camel.component.sjms.batch.BatchTestHelper.sendMessagesWithText;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class BatchConsumerExceptionHandlerTest extends JmsTestSupport {

    private static final String SJMS_FROMF_URI = "%s?batching=true&batchSize=5&exceptionHandler=#myHandler";
    private static final String SJMS_QUEUE_NAME = "sjms:queue:batch.consumer.BatchConsumerExceptionHandlerTest";
    private static final String MOCK_START = "mock:start";
    private static final String MOCK_FINISH = "mock:finish";
    private static final String EXCEPTION_MESSAGE = "Always throw an exception!";

    @BindToRegistry("myHandler")
    private CapturingExceptionHandler exceptionHandler = new CapturingExceptionHandler();

    @Test
    public void testBatchConsumerExceptionHandler() throws InterruptedException {
        MockEndpoint mockStart = getMockEndpoint(MOCK_START);
        MockEndpoint mockFinish = getMockEndpoint(MOCK_FINISH);

        mockStart.expectedMessageCount(1);

        mockFinish.expectedMessageCount(0);

        sendMessagesWithText(template, SJMS_QUEUE_NAME, 5, "Message %d!");

        await()
                .atMost(Duration.ofSeconds(10))
                .until(() -> exceptionHandler.counter.get() == 1);

        MockEndpoint.assertIsSatisfied(context);

        Throwable captured = exceptionHandler.captured.get();
        assertNotNull(captured, "The exceptionHandler should always get a cause");
        IllegalArgumentException cause = assertInstanceOf(IllegalArgumentException.class, captured.getCause());
        assertEquals(EXCEPTION_MESSAGE, cause.getMessage());
        assertEquals(1, exceptionHandler.counter.get());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                fromF(SJMS_FROMF_URI, SJMS_QUEUE_NAME)
                        .to(MOCK_START)
                        .process(exchange -> {
                            throw new IllegalArgumentException(EXCEPTION_MESSAGE);
                        })
                        .to(MOCK_FINISH);
            }
        };
    }

    private static final class CapturingExceptionHandler implements ExceptionHandler {

        private final AtomicInteger counter = new AtomicInteger();
        private final AtomicReference<Throwable> captured = new AtomicReference<>();

        @Override
        public void handleException(Throwable exception) {
            handleException(null, null, exception);
        }

        @Override
        public void handleException(String message, Throwable exception) {
            handleException(message, null, exception);
        }

        @Override
        public void handleException(String message, Exchange exchange, Throwable exception) {
            counter.incrementAndGet();
            captured.compareAndSet(null, exception);
        }
    }
}
