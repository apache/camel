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

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.AggregationStrategy;
import org.apache.camel.CamelExchangeException;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * The aggregation strategy of an enrich or a pollEnrich throws an Error (not a VirtualMachineError): the exchange fails
 * as for an exception of the strategy, and does not stay inflight.
 */
class EnricherAggregationStrategyThrowsErrorTest extends ContextTestSupport {

    private final AggregationStrategy strategy = (oldExchange, newExchange) -> {
        throw new NoClassDefFoundError("Simulated missing class");
    };
    private final AtomicInteger attempts = new AtomicInteger();

    @Test
    void testEnrich() throws Exception {

        Exchange out = template.asyncSend("direct:enrich", e -> e.getIn().setBody("Hello")).get(5, TimeUnit.SECONDS);

        assertInstanceOf(CamelExchangeException.class, out.getException());
        assertInstanceOf(NoClassDefFoundError.class, out.getException().getCause());
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> assertEquals(0, context.getInflightRepository().size()));
    }

    @Test
    void testPollEnrichReleasesTheFileAfterAnError() {
        template.sendBodyAndHeader(fileUri("data"), "Big file", Exchange.FILE_NAME, "AAA.dat");

        // the first aggregation fails with an Error
        Exchange out = template.send("direct:pollFile", e -> e.getIn().setBody("Start"));
        assertNotNull(out.getException());

        // the file must be released, so the second poll gets it again
        assertEquals("Big file", template.requestBody("direct:pollFile", "Start", String.class));
    }

    @Test
    void testPollEnrich() throws Exception {
        template.sendBody("seda:resource", "World");

        Exchange out = template.asyncSend("direct:pollEnrich", e -> e.getIn().setBody("Hello")).get(5, TimeUnit.SECONDS);

        assertInstanceOf(CamelExchangeException.class, out.getException());
        assertInstanceOf(NoClassDefFoundError.class, out.getException().getCause());
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> assertEquals(0, context.getInflightRepository().size()));
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:enrich").enrich("direct:resource", strategy).to("mock:result");
                from("direct:resource").transform(constant("World"));

                from("direct:pollFile").pollEnrich(fileUri("data?initialDelay=0&delay=10"), 2000, (original, resource) -> {
                    if (attempts.incrementAndGet() == 1) {
                        throw new NoClassDefFoundError("Simulated missing class");
                    }
                    original.getMessage().setBody(resource.getMessage().getBody(String.class));
                    return original;
                });

                from("direct:pollEnrich").pollEnrich("seda:resource", 5000, strategy).to("mock:result");
            }
        };
    }
}
