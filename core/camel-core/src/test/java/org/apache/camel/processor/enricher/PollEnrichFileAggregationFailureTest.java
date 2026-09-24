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
package org.apache.camel.processor.enricher;

import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.AggregationStrategy;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * When the aggregation of a polled file fails, the file is released (its on completion runs), so it can be polled
 * again.
 */
public class PollEnrichFileAggregationFailureTest extends ContextTestSupport {

    private final AtomicInteger attempts = new AtomicInteger();

    @Test
    public void testFileCanBePolledAgainAfterAggregationFailure() {
        template.sendBodyAndHeader(fileUri("data"), "Big file", Exchange.FILE_NAME, "AAA.dat");

        // the first aggregation fails
        assertThrows(Exception.class, () -> template.requestBody("direct:start", "Start"));

        // the file must be released so the second poll gets it again
        String out = template.requestBody("direct:start", "Start", String.class);
        assertEquals("Big file", out);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .pollEnrich(fileUri("data?initialDelay=0&delay=10"), 2000, new FailOnceStrategy());
            }
        };
    }

    private class FailOnceStrategy implements AggregationStrategy {
        @Override
        public Exchange aggregate(Exchange original, Exchange resource) {
            if (attempts.incrementAndGet() == 1) {
                throw new IllegalArgumentException("Forced");
            }
            if (resource != null) {
                original.getMessage().setBody(resource.getMessage().getBody(String.class));
            }
            return original;
        }
    }
}
