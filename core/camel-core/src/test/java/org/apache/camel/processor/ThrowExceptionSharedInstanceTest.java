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

import java.io.IOException;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * The exception instance a throwException step is configured with is shared by all the messages, so the error handlers
 * must not add the previous exception of each message to it as suppressed.
 */
public class ThrowExceptionSharedInstanceTest extends ContextTestSupport {

    private final IllegalStateException translated = new IllegalStateException("Translated");
    private final IllegalStateException afterCatch = new IllegalStateException("After catch");

    @Test
    public void testOnExceptionThrowsInstance() {
        for (int i = 0; i < 3; i++) {
            Exchange out = template.send("direct:onException", e -> {
            });
            assertSame(translated, out.getException());
        }
        assertEquals(0, translated.getSuppressed().length);
    }

    @Test
    public void testThrowsInstanceAfterDoCatch() {
        for (int i = 0; i < 3; i++) {
            Exchange out = template.send("direct:afterCatch", e -> {
            });
            assertSame(afterCatch, out.getException());
        }
        assertEquals(0, afterCatch.getSuppressed().length);
    }

    @Test
    public void testOnExceptionThrowsTypeAndMessage() {
        Exchange first = template.send("direct:typeAndMessage", e -> {
        });
        Exchange second = template.send("direct:typeAndMessage", e -> {
        });
        assertNotSame(first.getException(), second.getException());
        for (Exchange out : new Exchange[] { first, second }) {
            Exception e = assertInstanceOf(IllegalStateException.class, out.getException());
            assertEquals("Translated", e.getMessage());
            assertEquals(1, e.getSuppressed().length);
            assertInstanceOf(IOException.class, e.getSuppressed()[0]);
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:onException")
                        .onException(IOException.class).throwException(translated).end()
                        .throwException(IOException.class, "Failure of ${exchangeId}");

                from("direct:afterCatch")
                        .doTry()
                            .throwException(IOException.class, "Failure of ${exchangeId}")
                        .doCatch(IOException.class)
                            .to("mock:catch")
                        .end()
                        .throwException(afterCatch);

                from("direct:typeAndMessage")
                        .onException(IOException.class).throwException(IllegalStateException.class, "Translated").end()
                        .throwException(IOException.class, "Failure of ${exchangeId}");
            }
        };
    }
}
