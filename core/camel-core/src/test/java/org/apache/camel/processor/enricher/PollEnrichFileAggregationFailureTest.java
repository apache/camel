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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * When the aggregation of a polled file fails, the file is released (its on completion runs), so it can be polled
 * again.
 */
public class PollEnrichFileAggregationFailureTest extends ContextTestSupport {

    private final AtomicInteger attempts = new AtomicInteger();
    private final AtomicInteger redeliveryAttempts = new AtomicInteger();

    @Test
    public void testFileCanBePolledAgainAfterAggregationFailure() {
        template.sendBodyAndHeader(fileUri("data"), "Big file", Exchange.FILE_NAME, "AAA.dat");

        // the first aggregation fails
        assertThrows(Exception.class, () -> template.requestBody("direct:start", "Start"));

        // the file must be released so the second poll gets it again
        String out = template.requestBody("direct:start", "Start", String.class);
        assertEquals("Big file", out);
    }

    @Test
    public void testFileNotAggregatedIsNotCommittedWhenRedeliverySucceeds() throws Exception {
        template.sendBodyAndHeader(fileUri("inbox"), "A", Exchange.FILE_NAME, "a.txt");
        template.sendBodyAndHeader(fileUri("inbox"), "B", Exchange.FILE_NAME, "b.txt");

        // the first aggregation fails, and the redelivery polls and aggregates the other file
        String out = template.requestBody("direct:redelivery", "Start", String.class);
        assertTrue("A".equals(out) || "B".equals(out), "one of the files should be aggregated, was: " + out);

        // the file that was not aggregated must not be committed (moved to .camel), so it can be polled again
        String notAggregated = "A".equals(out) ? "b.txt" : "a.txt";
        assertFileExists(testFile("inbox/" + notAggregated));
        assertFileNotExists(testFile("inbox/" + out.toLowerCase() + ".txt"));
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .pollEnrich(fileUri("data?initialDelay=0&delay=10"), 2000, new FailOnceStrategy());

                from("direct:redelivery")
                        .errorHandler(defaultErrorHandler().maximumRedeliveries(1).redeliveryDelay(0))
                        .pollEnrich(fileUri("inbox?initialDelay=0&delay=10"), 2000, (original, resource) -> {
                            if (redeliveryAttempts.incrementAndGet() == 1) {
                                throw new IllegalStateException("Transient failure");
                            }
                            original.getMessage().setBody(resource.getMessage().getBody(String.class));
                            return original;
                        });
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
