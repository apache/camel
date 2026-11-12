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

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.seda.SedaEndpoint;
import org.apache.camel.sanitizer.junit.CamelSanitizer;
import org.apache.camel.sanitizer.junit.ExpectSanitizerViolation;
import org.apache.camel.support.ExchangeHelper;
import org.apache.camel.support.SynchronizationAdapter;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Rule R2 on routes: the commit a consumer adds to its exchange runs exactly once, for the unit of work that owns it.
 * The correct paths (a handover to a seda copy, a copy that seda does not queue, a split with a shared unit of work,
 * one on completion on many exchanges) report nothing; the broken ones are reported where they break the rule.
 */
@CamelSanitizer
class SanitizerCommitTest extends ContextTestSupport {

    private final Commit commit = new Commit();

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        context.addEndpoint("donetwice:x", new BrokenUserCode.DoneTwiceEndpoint("donetwice:x", context));
        return context;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:seda").routeId("toSeda").to("seda:a").id("send");
                from("seda:a").routeId("sedaA").to("mock:a");

                from("direct:full").routeId("full").to("seda:full?size=1&discardWhenFull=true").id("sendFull");

                from("direct:multi").routeId("multi").to("seda:multi?multipleConsumers=true").id("sendMulti");
                from("seda:multi?multipleConsumers=true").routeId("multi1").to("mock:multi1");
                from("seda:multi?multipleConsumers=true").routeId("multi2").to("mock:multi2");

                from("direct:parallel").routeId("parallel")
                        .split(body().tokenize(",")).parallelProcessing().shareUnitOfWork()
                            .process(e -> e.getExchangeExtension().addOnCompletion(new SynchronizationAdapter()))
                            .to("seda:a")
                        .end()
                        .to("mock:parallel");

                from("direct:split").routeId("split")
                        .split(body().tokenize(",")).shareUnitOfWork()
                            .to("seda:a")
                        .end()
                        .to("mock:split");

                from("direct:takeCommit").routeId("taking")
                        .process(e -> {
                            // a copy of the exchange keeps its unit of work: when the copy hands over, it hands over the
                            // commit of the exchange, which it was not created for (as a part with shareUnitOfWork did
                            // before CAMEL-26576)
                            Exchange part = e.copy();
                            Exchange queued = ExchangeHelper.createCorrelatedCopy(part, true);
                            // not queued after all: it gives the commit back, so it still runs once
                            queued.getExchangeExtension().handoverCompletions(e);
                        }).id("takeCommit")
                        .to("mock:taken");

                from("direct:dropCommit").routeId("dropping")
                        .process(e -> {
                            // hands over through the unit of work itself, not through the exchange, to a copy that is
                            // never routed: the commit leaves the unit of work and never runs
                            Exchange copy = ExchangeHelper.createCorrelatedCopy(e, false);
                            e.getUnitOfWork().handoverSynchronization(copy);
                        }).id("dropCommit");

                from("direct:nobody").routeId("nobody").to("seda:nobody").id("sendNobody");

                from("direct:topic").routeId("topic")
                        .loadBalance().topic()
                            .to("seda:a")
                            .to("mock:topic")
                        .end();

                from("donetwice:x").routeId("doneTwice").to("mock:doneTwice");
            }
        };
    }

    @Test
    void aCommitHandedOverToASedaCopyRunsOnceWithTheCopy() throws Exception {
        getMockEndpoint("mock:a").expectedMessageCount(1);

        template.send("direct:seda", e -> e.getExchangeExtension().addOnCompletion(commit));

        assertMockEndpointsSatisfied();
        awaitRuns(1);
        assertNoViolations();
        assertTrue(Sanitizer.get(context).report().contains("commits followed"), Sanitizer.get(context).report());
    }

    @Test
    void oneOnCompletionOnManyExchangesRunsOnceForEach() throws Exception {
        // a consumer may add the same object to every exchange: each add is a commit of its own
        getMockEndpoint("mock:a").expectedMessageCount(3);

        for (int i = 0; i < 3; i++) {
            template.send("direct:seda", e -> e.getExchangeExtension().addOnCompletion(commit));
        }

        assertMockEndpointsSatisfied();
        awaitRuns(3);
        assertNoViolations();
    }

    @Test
    void aCommitTakenOutBySedaWithManyConsumersRunsOnce() throws Exception {
        // the seda consumer takes the commits out of the copy and runs them when all its consumers are done
        getMockEndpoint("mock:multi1").expectedMessageCount(1);
        getMockEndpoint("mock:multi2").expectedMessageCount(1);

        template.send("direct:multi", e -> e.getExchangeExtension().addOnCompletion(commit));

        assertMockEndpointsSatisfied();
        awaitRuns(1);
        assertNoViolations();
    }

    @Test
    void theCommitOfAParallelSplitWithASharedUnitOfWorkRunsOnceWithIt() throws Exception {
        // the parts hand over their own on completions to their seda copies at the same time, never the commit
        getMockEndpoint("mock:a").expectedMessageCount(4);
        getMockEndpoint("mock:parallel").expectedMessageCount(1);

        template.send("direct:parallel", e -> {
            e.getMessage().setBody("a,b,c,d");
            e.getExchangeExtension().addOnCompletion(commit);
        });

        assertMockEndpointsSatisfied();
        awaitRuns(1);
        assertEquals(List.of("parallel"), commit.routes);
        assertNoViolations();
    }

    @Test
    void theCommitOfAnExchangeWhoseTopicCopyIsSentToSedaRunsOnceWithTheExchange() throws Exception {
        // the copies of the topic load balancer use the unit of work of the exchange, as parts with shareUnitOfWork
        getMockEndpoint("mock:a").expectedMessageCount(1);
        getMockEndpoint("mock:topic").expectedMessageCount(1);

        template.send("direct:topic", e -> e.getExchangeExtension().addOnCompletion(commit));

        assertMockEndpointsSatisfied();
        awaitRuns(1);
        assertEquals(List.of("topic"), commit.routes);
        assertNoViolations();
    }

    @Test
    void aCopyThatSedaDoesNotQueueGivesTheCommitBack() {
        // the first copy fills the queue (it has no consumer), the second is discarded and gives its commit back
        template.send("direct:full", e -> e.getExchangeExtension().addOnCompletion(commit));
        template.send("direct:full", e -> e.getExchangeExtension().addOnCompletion(commit));
        awaitRuns(1);

        // the commit of the queued copy runs when the queue is purged (taken out of the copy and run)
        context.getEndpoint("seda:full?size=1&discardWhenFull=true", SedaEndpoint.class).purgeQueue();

        awaitRuns(2);
        assertNoViolations();
    }

    @Test
    void aSplitWithASharedUnitOfWorkKeepsTheCommitOfItsParent() throws Exception {
        getMockEndpoint("mock:a").expectedMessageCount(2);
        getMockEndpoint("mock:split").expectedMessageCount(1);

        template.send("direct:split", e -> {
            e.getMessage().setBody("a,b");
            e.getExchangeExtension().addOnCompletion(commit);
        });

        assertMockEndpointsSatisfied();
        awaitRuns(1);
        assertEquals(List.of("split"), commit.routes);
        assertNoViolations();
    }

    @Test
    @ExpectSanitizerViolation(value = Violation.COMMIT_HANDED_OVER_BY_OTHER_EXCHANGE, step = "takeCommit")
    void aCommitHandedOverByAnotherExchangeIsReportedAtTheHandover() {
        Exchange out = template.send("direct:takeCommit", e -> e.getExchangeExtension().addOnCompletion(commit));
        assertNull(out.getException());
        awaitRuns(1);

        SanitizerViolation v = awaitOne(Violation.COMMIT_HANDED_OVER_BY_OTHER_EXCHANGE);
        assertEquals("takeCommit", v.getStepId());
        assertEquals("taking", v.getRouteId());
        assertEquals(Commit.class.getName(), v.getProcessor());
        assertEquals(out.getExchangeId(), v.getExchangeId());
        assertTrue(v.getText().contains("its unit of work was created for exchange " + out.getExchangeId()), v.getText());
        assertTrue(v.getText().contains("(R2)"), v.getText());
        // the stack of the handover starts in the code that handed over
        assertTrue(v.getText().contains("at org.apache.camel.support.AbstractExchange.handoverCompletions"), v.getText());
        assertTrue(v.getText().contains("setSharedUnitOfWork"), v.getText());
    }

    @Test
    @ExpectSanitizerViolation(Violation.COMMIT_NEVER_RUN)
    void aCommitTakenOutOfItsUnitOfWorkNeverRuns() {
        Exchange out = template.send("direct:dropCommit", e -> e.getExchangeExtension().addOnCompletion(commit));
        assertNull(out.getException());

        SanitizerViolation v = awaitOne(Violation.COMMIT_NEVER_RUN);
        assertEquals(out.getExchangeId(), v.getExchangeId());
        assertEquals("dropping", v.getRouteId());
        assertNull(v.getStepId());
        assertTrue(v.getText().contains("is done, and the commit was neither run nor handed over or taken out"),
                v.getText());
        assertEquals(0, commit.runs());
    }

    @Test
    @ExpectSanitizerViolation(Violation.COMMIT_NEVER_RUN)
    void aCommitInACopyThatIsNeverRoutedIsReportedWhenTheContextStops() {
        Exchange out = template.send("direct:nobody", e -> e.getExchangeExtension().addOnCompletion(commit));
        assertNull(out.getException());
        Sanitizer sanitizer = Sanitizer.get(context);
        assertEquals(0, sanitizer.getViolationCount(Violation.COMMIT_NEVER_RUN));

        // the queue of seda:nobody has no consumer, so the copy in it is dropped
        context.stop();

        SanitizerViolation v = awaitOne(sanitizer, Violation.COMMIT_NEVER_RUN);
        assertEquals(out.getExchangeId(), v.getExchangeId());
        assertTrue(v.getText().contains("the CamelContext stopped while it waited in exchange"), v.getText());
        assertTrue(v.getText().contains("which was never routed"), v.getText());
    }

    @Test
    @ExpectSanitizerViolation(Violation.COMMIT_RUN_TWICE)
    void aCustomConsumerWhoseCommitRunsTwiceIsReportedWithItsClass() throws Exception {
        getMockEndpoint("mock:doneTwice").expectedMessageCount(1);
        BrokenUserCode.DoneTwiceConsumer consumer
                = context.getEndpoint("donetwice:x", BrokenUserCode.DoneTwiceEndpoint.class).consumer;

        consumer.receive("x");

        assertMockEndpointsSatisfied();
        assertEquals(2, consumer.commits.get());
        SanitizerViolation v = awaitOne(Violation.COMMIT_RUN_TWICE);
        assertEquals("doneTwice", v.getRouteId());
        assertEquals(BrokenUserCode.DoneTwiceConsumer.MessageCommit.class.getName(), v.getProcessor());
        assertTrue(v.getText().contains("consumer " + BrokenUserCode.DoneTwiceConsumer.class.getName()), v.getText());
        assertTrue(v.getText().contains("first run, with exchange " + v.getExchangeId()), v.getText());
        assertTrue(v.getText().contains("run again, with exchange " + v.getExchangeId()), v.getText());
        // both stacks show the consumer that called done
        assertTrue(v.getText().contains("at " + BrokenUserCode.DoneTwiceConsumer.class.getName() + ".receive"),
                v.getText());
    }

    private void awaitRuns(int runs) {
        await().atMost(Duration.ofSeconds(10)).until(() -> commit.runs() >= runs);
        assertEquals(runs, commit.runs());
    }

    private void assertNoViolations() {
        Sanitizer sanitizer = Sanitizer.get(context);
        assertNotNull(sanitizer, "the sanitizer is installed for a test marked with @CamelSanitizer");
        assertEquals(List.of(), sanitizer.getViolations());
    }

    private SanitizerViolation awaitOne(Violation kind) {
        Sanitizer sanitizer = Sanitizer.get(context);
        assertNotNull(sanitizer, "the sanitizer is installed for a test marked with @CamelSanitizer");
        return awaitOne(sanitizer, kind);
    }

    private static SanitizerViolation awaitOne(Sanitizer sanitizer, Violation kind) {
        await().atMost(Duration.ofSeconds(10)).until(() -> sanitizer.getViolationCount(kind) > 0);
        List<SanitizerViolation> found = sanitizer.getViolations();
        assertEquals(1, found.size(), found::toString);
        assertEquals(kind, found.get(0).getKind());
        return found.get(0);
    }

    /**
     * The commit of a consumer, which records the route of each exchange it runs with.
     */
    static final class Commit extends SynchronizationAdapter {

        final List<String> routes = new CopyOnWriteArrayList<>();

        @Override
        public void onDone(Exchange exchange) {
            routes.add(exchange.getFromRouteId());
        }

        int runs() {
            return routes.size();
        }
    }
}
