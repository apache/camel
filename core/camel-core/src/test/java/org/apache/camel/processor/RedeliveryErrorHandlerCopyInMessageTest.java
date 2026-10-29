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
package org.apache.camel.processor;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.SafeCopyProperty;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * The redelivery error handler keeps a copy of the IN message for each node, so it can redeliver the original message.
 * It must not copy the whole exchange for that, as this happens for every node on the success path.
 */
public class RedeliveryErrorHandlerCopyInMessageTest extends ContextTestSupport {

    private final AtomicInteger exchangeCopies = new AtomicInteger();
    private final List<String> attempts = new ArrayList<>();

    @Test
    public void testSuccessPathDoesNotCopyExchange() {
        Exchange out = template.send("direct:success", e -> e.getIn().setBody("Hello World"));

        assertNull(out.getException());
        assertEquals("Hello World", out.getMessage().getBody());
        // every copy of the exchange calls safeCopy on this property, so 0 means the nodes after it did not copy
        assertEquals(0, exchangeCopies.get());
    }

    @Test
    public void testRedeliveryRestoresOriginalInMessage() {
        Exchange out = template.send("direct:redeliver", e -> {
            e.getIn().setBody("Hello World");
            e.getIn().setHeader("foo", "original");
        });

        assertNull(out.getException());
        // each attempt sees the original body and header, not what the failed attempt changed
        assertEquals(List.of("Hello World/original", "Hello World/original", "Hello World/original"), attempts);
        assertEquals("Bye World", out.getMessage().getBody());
        // exchange properties are not part of the message, so they are not restored on redelivery
        assertEquals(2, out.getProperty("failedAttempts", Integer.class));
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                errorHandler(defaultErrorHandler().maximumRedeliveries(3).redeliveryDelay(0));

                from("direct:success")
                        .process(e -> e.getExchangeExtension().setSafeCopyProperty("counter", new CountingProperty()))
                        .setHeader("step", constant(1))
                        .setHeader("step", constant(2))
                        .process(e -> e.getIn().setHeader("step", 3));

                from("direct:redeliver")
                        .process(e -> {
                            attempts.add(e.getIn().getBody() + "/" + e.getIn().getHeader("foo"));
                            e.getIn().setBody("Changed");
                            e.getIn().setHeader("foo", "changed");
                            if (attempts.size() < 3) {
                                int failed = e.getProperty("failedAttempts", 0, Integer.class);
                                e.setProperty("failedAttempts", failed + 1);
                                throw new IllegalArgumentException("Forced attempt " + attempts.size());
                            }
                            e.getIn().setBody("Bye World");
                        });
            }
        };
    }

    private final class CountingProperty implements SafeCopyProperty {

        @Override
        public SafeCopyProperty safeCopy() {
            exchangeCopies.incrementAndGet();
            return this;
        }
    }
}
