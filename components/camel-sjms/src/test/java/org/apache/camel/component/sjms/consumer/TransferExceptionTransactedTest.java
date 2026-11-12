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
package org.apache.camel.component.sjms.consumer;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.sjms.support.JmsTestSupport;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * With transferException, an exception that is sent back as the reply commits the transacted session, whatever its
 * type: the reply reaches the caller and the request is not redelivered.
 */
public class TransferExceptionTransactedTest extends JmsTestSupport {

    private static final String REQUEST_OPTIONS = "?transferException=true&objectMessageEnabled=true&requestTimeout=5000";
    private static final String CONSUMER_OPTIONS = "?transacted=true&transferException=true&objectMessageEnabled=true";

    private final AtomicInteger counter = new AtomicInteger();
    private final AtomicInteger wrappedCounter = new AtomicInteger();

    @Override
    protected boolean useJmx() {
        return false;
    }

    @Test
    public void testRuntimeCamelExceptionIsSentBack() {
        assertSentBackOnce("sjms:queue:TransferExceptionTransactedTest", counter);
    }

    @Test
    public void testExceptionCausedByRuntimeCamelExceptionIsSentBack() {
        assertSentBackOnce("sjms:queue:TransferExceptionTransactedTest.wrapped", wrappedCounter);
    }

    private void assertSentBackOnce(String uri, AtomicInteger processed) {
        CamelExecutionException e = assertThrows(CamelExecutionException.class,
                () -> template.requestBody(uri + REQUEST_OPTIONS, "Hello World"));
        assertEquals("Kaboom", e.getCause().getMessage());

        // the request was committed together with the reply, so it is not redelivered
        await().during(1, TimeUnit.SECONDS).atMost(5, TimeUnit.SECONDS).until(() -> processed.get() == 1);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("sjms:queue:TransferExceptionTransactedTest" + CONSUMER_OPTIONS)
                        .process(exchange -> {
                            counter.incrementAndGet();
                            throw new RuntimeCamelException("Kaboom");
                        });

                from("sjms:queue:TransferExceptionTransactedTest.wrapped" + CONSUMER_OPTIONS)
                        .process(exchange -> {
                            wrappedCounter.incrementAndGet();
                            throw new IllegalStateException("Kaboom", new RuntimeCamelException("Inner"));
                        });
            }
        };
    }
}
