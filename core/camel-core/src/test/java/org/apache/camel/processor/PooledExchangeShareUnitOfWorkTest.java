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

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.ExtendedCamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.engine.PooledExchangeFactory;
import org.apache.camel.impl.engine.PooledProcessorExchangeFactory;
import org.apache.camel.processor.aggregate.UseLatestAggregationStrategy;
import org.apache.camel.support.SynchronizationAdapter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * With pooled exchanges, releasing a part (or the resource exchange of enrich) that shares the unit of work must not
 * reset the unit of work of the parent exchange: its on completions (such as the commit of its consumer) must still
 * run.
 */
public class PooledExchangeShareUnitOfWorkTest extends ContextTestSupport {

    private final AtomicInteger completed = new AtomicInteger();
    private final AtomicInteger failed = new AtomicInteger();

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = super.createCamelContext();
        ExtendedCamelContext ecc = camelContext.getCamelContextExtension();
        ecc.setExchangeFactory(new PooledExchangeFactory());
        ecc.setProcessorExchangeFactory(new PooledProcessorExchangeFactory());
        return camelContext;
    }

    @Test
    public void testSplitShareUnitOfWork() throws Exception {
        assertParentCompletes("direct:split", List.of("A", "B"), 2);
    }

    @Test
    public void testParallelSplitShareUnitOfWork() throws Exception {
        assertParentCompletes("direct:splitParallel", List.of("A", "B", "C"), 3);
    }

    @Test
    public void testMulticastShareUnitOfWork() throws Exception {
        assertParentCompletes("direct:multicast", "A", 2);
    }

    @Test
    public void testEnrichShareUnitOfWork() throws Exception {
        assertParentCompletes("direct:enrich", "A", 1);
    }

    @Test
    public void testSplitShareUnitOfWorkFailed() throws Exception {
        getMockEndpoint("mock:part").expectedMessageCount(2);

        Exchange out = template.send("direct:splitFail", e -> e.getMessage().setBody(List.of("A", "FAIL")));

        assertMockEndpointsSatisfied();
        assertEquals(true, out.isFailed());
        assertEquals(0, completed.get());
        assertEquals(1, failed.get(), "the parent's on failure must run once");
        assertEquals(0, context.getInflightRepository().size());
    }

    @Test
    public void testSplitWithoutShareUnitOfWork() throws Exception {
        // the split releases the copy of the parent it creates the parts from, which holds the parent's unit of work
        assertParentCompletes("direct:splitNoShare", List.of("A", "B"), 2);
    }

    @Test
    public void testWithoutSplit() throws Exception {
        // control: no exchange is released before the parent completes
        assertParentCompletes("direct:plain", "A", 1);
    }

    private void assertParentCompletes(String uri, Object body, int parts) throws Exception {
        getMockEndpoint("mock:part").expectedMessageCount(parts);
        getMockEndpoint("mock:result").expectedMessageCount(1);

        template.sendBody(uri, body);

        assertMockEndpointsSatisfied();
        assertEquals(1, completed.get(), "the parent's on completion (its consumer's commit) must run once");
        assertEquals(0, failed.get());
        assertEquals(0, context.getInflightRepository().size());
    }

    private void addCommit(Exchange exchange) {
        // stands for the commit or rollback the consumer of the parent exchange registers
        exchange.getExchangeExtension().addOnCompletion(new SynchronizationAdapter() {
            @Override
            public void onComplete(Exchange exchange) {
                completed.incrementAndGet();
            }

            @Override
            public void onFailure(Exchange exchange) {
                failed.incrementAndGet();
            }
        });
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:split").process(e -> addCommit(e))
                        .split(body()).shareUnitOfWork().to("mock:part").end()
                        .to("mock:result");

                from("direct:splitParallel").process(e -> addCommit(e))
                        .split(body()).shareUnitOfWork().parallelProcessing().to("mock:part").end()
                        .to("mock:result");

                from("direct:multicast").process(e -> addCommit(e))
                        .multicast().shareUnitOfWork().to("mock:part", "mock:part").end()
                        .to("mock:result");

                from("direct:enrich").process(e -> addCommit(e))
                        .enrich().constant("direct:resource")
                        .aggregationStrategy(new UseLatestAggregationStrategy()).shareUnitOfWork()
                        .to("mock:result");

                from("direct:resource").to("mock:part").transform(constant("Resource"));

                from("direct:splitFail").errorHandler(noErrorHandler()).process(e -> addCommit(e))
                        .split(body()).shareUnitOfWork().stopOnException()
                        .to("mock:part")
                        .filter(body().isEqualTo("FAIL")).throwException(new IllegalArgumentException("Forced")).end()
                        .end()
                        .to("mock:result");

                from("direct:plain").process(e -> addCommit(e)).to("mock:part").to("mock:result");

                from("direct:splitNoShare").process(e -> addCommit(e))
                        .split(body()).to("mock:part").end()
                        .to("mock:result");
            }
        };
    }
}
