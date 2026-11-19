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
package org.apache.camel.sanitizer;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.ExchangePattern;
import org.apache.camel.builder.AggregationStrategies;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.sanitizer.junit.CamelSanitizer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Rule R3, its second part: the handovers of an exchange from one thread to another that are correct are not reported.
 * Each point opens a watch window here (sampling 1), with the default window: seda, the threads EIP, a parallel split,
 * multicast and recipient list, an asynchronous delayer, an asynchronous processor that calls its callback on another
 * thread, a caller that waits for its task, a wire tap, an enrich, a loop that copies (its copies keep the id of the
 * exchange), an aggregator and a parallel on completion.
 */
@CamelSanitizer(overlapSampling = 1)
class SanitizerOverlapHandoverTest extends ContextTestSupport {

    private static final int MANY = 50;

    private final ExecutorService pool = Executors.newFixedThreadPool(4, r -> new Thread(r, "user-pool"));

    @AfterEach
    void shutdownPool() {
        pool.shutdownNow();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:sub").setHeader("sub", constant(true));
                from("direct:asyncSub").process(new BrokenUserCode.CorrectAsync(pool)).setHeader("asyncSub", constant(true));

                from("direct:seda")
                        .to("seda:inOnly").id("toSedaInOnly")
                        .to(ExchangePattern.InOut, "seda:inOut").id("toSedaInOut")
                        .to("mock:result");
                from("seda:inOnly").to("direct:asyncSub");
                from("seda:inOut").to("direct:asyncSub").setBody(constant("reply"));

                from("direct:threads").threads(2).id("threads").to("direct:sub").to("mock:result");

                from("direct:split")
                        .split(body().tokenize(",")).parallelProcessing().id("split")
                            .process(new BrokenUserCode.CorrectAsync(pool))
                            .to("direct:sub")
                        .end()
                        .to("mock:result");
                from("direct:multicast")
                        .multicast().parallelProcessing().id("multicast")
                            .to("direct:sub", "direct:asyncSub")
                        .end()
                        .to("mock:result");
                from("direct:recipients")
                        .recipientList(constant("direct:sub,direct:asyncSub")).parallelProcessing().id("recipients")
                        .to("mock:result");

                from("direct:delay").delay(1).asyncDelayed().id("delay").to("mock:result");
                from("direct:async").process(new BrokenUserCode.CorrectAsync(pool)).id("async").to("mock:result");
                from("direct:waits")
                        .process(new BrokenUserCode.WaitsForItsTask(pool, template, "direct:asyncSub")).id("breaker")
                        .to("mock:result");

                from("direct:wireTap").wireTap("direct:asyncSub").id("tap").to("mock:result");
                from("direct:enrich").enrich("direct:asyncSub").id("enrich").to("mock:result");
                from("direct:loop")
                        .loop(3).copy().id("loop")
                            .process(new BrokenUserCode.CorrectAsync(pool))
                        .end()
                        .to("mock:result");
                from("direct:aggregate")
                        .aggregate(constant(true), AggregationStrategies.groupedBody()).completionSize(2).id("aggregate")
                        .to("direct:asyncSub")
                        .to("mock:aggregated");
                from("direct:onCompletion")
                        .onCompletion().parallelProcessing()
                            .to("direct:asyncSub")
                            .to("mock:done")
                        .end()
                        .to("direct:asyncSub")
                        .to("mock:result");
            }
        };
    }

    @Test
    void seda() throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived("reply");
        template.sendBody("direct:seda", "x");
        assertMockEndpointsSatisfied();
        assertNoOverlaps();
    }

    @Test
    void threadsEip() throws Exception {
        getMockEndpoint("mock:result").expectedHeaderReceived("sub", true);
        template.sendBody("direct:threads", "x");
        assertMockEndpointsSatisfied();
        assertNoOverlaps();
    }

    @Test
    void parallelSplitMulticastAndRecipientList() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(3);
        template.sendBody("direct:split", "a,b,c,d");
        template.sendBody("direct:multicast", "x");
        template.sendBody("direct:recipients", "x");
        assertMockEndpointsSatisfied();
        assertNoOverlaps();
    }

    @Test
    void asynchronousDelayerAndProcessors() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(3);
        template.sendBody("direct:delay", "x");
        template.sendBody("direct:async", "x");
        template.sendBody("direct:waits", "x");
        assertMockEndpointsSatisfied();
        assertNoOverlaps();
    }

    @Test
    void wireTapEnrichLoopAggregateAndOnCompletion() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(4);
        getMockEndpoint("mock:aggregated").expectedMessageCount(1);
        // the parallel on completion is done before the test ends
        getMockEndpoint("mock:done").expectedMessageCount(1);
        template.sendBody("direct:wireTap", "x");
        template.sendBody("direct:enrich", "x");
        template.sendBody("direct:loop", "x");
        template.sendBody("direct:aggregate", "a");
        template.sendBody("direct:aggregate", "b");
        template.sendBody("direct:onCompletion", "x");
        assertMockEndpointsSatisfied();
        assertNoOverlaps();
    }

    @Test
    void manyExchangesAtOnce() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(5 * MANY);
        List<CompletableFuture<?>> sent = new ArrayList<>();
        for (int i = 0; i < MANY; i++) {
            sent.add(template.asyncSendBody("direct:seda", i));
            sent.add(template.asyncSendBody("direct:threads", i));
            sent.add(template.asyncSendBody("direct:split", "a,b"));
            sent.add(template.asyncSendBody("direct:async", i));
            sent.add(template.asyncSendBody("direct:loop", i));
        }
        CompletableFuture.allOf(sent.toArray(new CompletableFuture[0])).get(60, TimeUnit.SECONDS);
        assertMockEndpointsSatisfied(60, TimeUnit.SECONDS);
        assertNoOverlaps();
    }

    private void assertNoOverlaps() {
        Sanitizer sanitizer = Sanitizer.get(context);
        assertNotNull(sanitizer, "the sanitizer is installed for a test marked with @CamelSanitizer");
        assertEquals(1, sanitizer.getOverlaps().getSampling());
        assertTrue(sanitizer.getOverlaps().getWindowCount() > 0, sanitizer::report);
        assertEquals(List.of(), sanitizer.getViolations());
    }
}
