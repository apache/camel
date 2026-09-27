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

import java.util.Iterator;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * A streaming split that stops on an exception must not read, prepare or create exchanges for the parts after the
 * failure.
 */
public class SplitterStreamingStopOnExceptionReadAheadTest extends ContextTestSupport {

    private final AtomicInteger read = new AtomicInteger();
    private final AtomicInteger prepared = new AtomicInteger();

    @Test
    public void testStopOnExceptionDoesNotReadRemainingParts() {
        Iterator<Integer> parts = new Iterator<>() {
            private int next;

            @Override
            public boolean hasNext() {
                return next < 100;
            }

            @Override
            public Integer next() {
                read.incrementAndGet();
                return next++;
            }
        };

        assertThrows(Exception.class, () -> template.sendBody("direct:start", parts));

        // part 0 is routed, part 1 fails and stops the split
        assertEquals(2, prepared.get(), "onPrepare should only be called for the parts that are routed");
        assertEquals(2, read.get(), "the parts after the failure should not be read");
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .split(body()).streaming().stopOnException().onPrepare(e -> prepared.incrementAndGet())
                        .process(e -> {
                            if (e.getMessage().getBody(Integer.class) == 1) {
                                throw new IllegalArgumentException("Forced");
                            }
                        })
                        .end();
            }
        };
    }
}
