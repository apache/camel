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

import java.nio.file.Files;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.NotifyBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.Synchronization;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * With shareUnitOfWork, a part that sends InOnly to seda must not hand the on completions of the shared unit of work
 * (such as the consumer's commit) to its copy: they belong to the whole exchange.
 */
class ShareUnitOfWorkSedaHandoverTest extends ContextTestSupport {

    private final List<String> events = new CopyOnWriteArrayList<>();

    @Test
    void testFileNotCommittedWhenALaterPartFails() throws Exception {
        NotifyBuilder file = new NotifyBuilder(context).fromRoute("file").whenDone(1).create();
        NotifyBuilder seda = new NotifyBuilder(context).fromRoute("seda").whenDone(1).create();

        template.sendBodyAndHeader(fileUri(), "a,bad", Exchange.FILE_NAME, "lines.txt");

        assertTrue(file.matches(20, TimeUnit.SECONDS));
        assertTrue(seda.matches(20, TimeUnit.SECONDS));
        // the split failed, so the file is a failed file, and the part sent to seda does not commit it
        assertTrue(Files.exists(testFile("failed/lines.txt")), "the file should be moved to failed");
        assertFalse(Files.exists(testFile("done/lines.txt")), "the file must not be moved to done");
    }

    @Test
    void testFileCommittedWhenAllPartsSucceed() throws Exception {
        NotifyBuilder file = new NotifyBuilder(context).fromRoute("file").whenDone(1).create();
        NotifyBuilder seda = new NotifyBuilder(context).fromRoute("seda").whenDone(1).create();

        template.sendBodyAndHeader(fileUri(), "a,b", Exchange.FILE_NAME, "lines.txt");

        assertTrue(file.matches(20, TimeUnit.SECONDS));
        assertTrue(seda.matches(20, TimeUnit.SECONDS));
        assertTrue(Files.exists(testFile("done/lines.txt")), "the file should be moved to done");
        assertFalse(Files.exists(testFile("failed/lines.txt")), "the file must not be moved to failed");
    }

    @Test
    void testSplitFailsAfterSedaPart() throws Exception {
        NotifyBuilder seda = new NotifyBuilder(context).fromRoute("seda").whenDone(1).create();

        Exchange out = template.send("direct:split", e -> {
            e.getExchangeExtension().addOnCompletion(new Recorder("commit"));
            e.getIn().setBody("a,bad");
        });

        assertTrue(out.isFailed());
        assertTrue(seda.matches(20, TimeUnit.SECONDS));
        assertEquals(List.of("commit:failure:" + out.getExchangeId()), events);
    }

    @Test
    void testMulticastFailsAfterSedaPart() throws Exception {
        NotifyBuilder seda = new NotifyBuilder(context).fromRoute("seda").whenDone(1).create();

        Exchange out = template.send("direct:multicast", e -> {
            e.getExchangeExtension().addOnCompletion(new Recorder("commit"));
            e.getIn().setBody("bad");
        });

        assertTrue(out.isFailed());
        assertTrue(seda.matches(20, TimeUnit.SECONDS));
        assertEquals(List.of("commit:failure:" + out.getExchangeId()), events);
    }

    @Test
    void testRecipientListFailsAfterSedaPart() throws Exception {
        NotifyBuilder seda = new NotifyBuilder(context).fromRoute("seda").whenDone(1).create();

        Exchange out = template.send("direct:recipientList", e -> {
            e.getExchangeExtension().addOnCompletion(new Recorder("commit"));
            e.getIn().setBody("bad");
        });

        assertTrue(out.isFailed());
        assertTrue(seda.matches(20, TimeUnit.SECONDS));
        assertEquals(List.of("commit:failure:" + out.getExchangeId()), events);
    }

    @Test
    void testSplitSucceedsWhenSedaCopyFails() throws Exception {
        NotifyBuilder seda = new NotifyBuilder(context).fromRoute("seda").whenDone(1).create();

        Exchange out = template.send("direct:split", e -> {
            e.getExchangeExtension().addOnCompletion(new Recorder("commit"));
            e.getIn().setBody("a-fail-later,b");
        });

        assertFalse(out.isFailed());
        assertTrue(seda.matches(20, TimeUnit.SECONDS));
        // the split succeeded: the commit is the outcome of the split, not of the copy sent to seda
        assertEquals(List.of("commit:complete:" + out.getExchangeId()), events);
    }

    @Test
    void testPartHandsOverItsOwnOnCompletion() throws Exception {
        NotifyBuilder seda = new NotifyBuilder(context).fromRoute("seda").whenDone(1).create();
        getMockEndpoint("mock:seda").expectedMessageCount(1);

        Exchange out = template.send("direct:own", e -> {
            e.getExchangeExtension().addOnCompletion(new Recorder("commit"));
            e.getIn().setBody("a");
        });

        assertFalse(out.isFailed());
        assertTrue(seda.matches(20, TimeUnit.SECONDS));
        assertMockEndpointsSatisfied();
        String copyId = getMockEndpoint("mock:seda").getReceivedExchanges().get(0).getExchangeId();
        // the on completion the part added goes with its seda copy (as without shareUnitOfWork), the consumer's stays
        assertEquals(2, events.size(), "events: " + events);
        assertTrue(events.contains("part:complete:" + copyId), "events: " + events);
        assertTrue(events.contains("commit:complete:" + out.getExchangeId()), "events: " + events);
    }

    @Test
    void testPartSendsToSedaTwice() throws Exception {
        NotifyBuilder seda = new NotifyBuilder(context).fromRoute("seda").whenDone(2).create();
        getMockEndpoint("mock:seda").expectedMessageCount(2);

        Exchange out = template.send("direct:twice", e -> {
            e.getExchangeExtension().addOnCompletion(new Recorder("commit"));
            e.getIn().setBody("a");
        });

        assertFalse(out.isFailed());
        assertTrue(seda.matches(20, TimeUnit.SECONDS));
        assertMockEndpointsSatisfied();
        List<Exchange> copies = getMockEndpoint("mock:seda").getReceivedExchanges();
        // each copy takes the on completions the part added before it was sent, the consumer's stays with the split
        assertEquals(3, events.size(), "events: " + events);
        assertTrue(events.contains("first:complete:" + copies.get(0).getExchangeId()), "events: " + events);
        assertTrue(events.contains("second:complete:" + copies.get(1).getExchangeId()), "events: " + events);
        assertTrue(events.contains("commit:complete:" + out.getExchangeId()), "events: " + events);
    }

    @Test
    void testNestedSplitFailsAfterSedaPart() throws Exception {
        NotifyBuilder seda = new NotifyBuilder(context).fromRoute("seda").whenDone(1).create();

        Exchange out = template.send("direct:nested", e -> {
            e.getExchangeExtension().addOnCompletion(new Recorder("commit"));
            e.getIn().setBody("a;bad");
        });

        assertTrue(out.isFailed());
        assertTrue(seda.matches(20, TimeUnit.SECONDS));
        assertEquals(List.of("commit:failure:" + out.getExchangeId()), events);
    }

    @Test
    void testSplitWithoutSedaControl() throws Exception {
        Exchange out = template.send("direct:split-no-seda", e -> {
            e.getExchangeExtension().addOnCompletion(new Recorder("commit"));
            e.getIn().setBody("a,bad");
        });

        assertTrue(out.isFailed());
        assertEquals(List.of("commit:failure:" + out.getExchangeId()), events);
    }

    @Test
    void testSedaWithoutShareUnitOfWorkStillHandsOver() throws Exception {
        NotifyBuilder seda = new NotifyBuilder(context).fromRoute("seda").whenDone(1).create();

        Exchange out = template.send("direct:plain", e -> {
            e.getExchangeExtension().addOnCompletion(new Recorder("commit"));
            e.getIn().setBody("a");
        });

        assertFalse(out.isFailed());
        assertTrue(seda.matches(20, TimeUnit.SECONDS));
        // a plain InOnly send to seda hands the on completions over to the copy (unchanged)
        assertEquals(1, events.size(), "events: " + events);
        assertTrue(events.get(0).startsWith("commit:complete:"), "events: " + events);
        assertFalse(events.get(0).endsWith(out.getExchangeId()), "should run for the seda copy: " + events);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from(fileUri("?initialDelay=0&delay=10&move=done&moveFailed=failed")).routeId("file")
                        .split(body().tokenize(",")).shareUnitOfWork()
                            .to("direct:line")
                        .end();

                from("direct:split")
                        .split(body().tokenize(",")).shareUnitOfWork()
                            .to("direct:line")
                        .end();

                from("direct:multicast")
                        .multicast().shareUnitOfWork()
                            .to("seda:line", "direct:line")
                        .end();

                from("direct:recipientList")
                        .recipientList(constant("seda:line,direct:line")).shareUnitOfWork();

                from("direct:twice")
                        .split(body().tokenize(",")).shareUnitOfWork()
                            .process(e -> e.getExchangeExtension().addOnCompletion(new Recorder("first")))
                            .to("seda:line")
                            .process(e -> e.getExchangeExtension().addOnCompletion(new Recorder("second")))
                            .to("seda:line")
                        .end();

                from("direct:nested")
                        .split(body().tokenize(",")).shareUnitOfWork()
                        .split(body().tokenize(";")).shareUnitOfWork()
                        .to("direct:line")
                        .end()
                        .end();

                from("direct:split-no-seda")
                        .split(body().tokenize(",")).shareUnitOfWork()
                            .filter(body().isEqualTo("bad"))
                                .throwException(new IllegalArgumentException("bad line"))
                            .end()
                            .to("mock:line")
                        .end();

                from("direct:plain").to("seda:line");

                from("direct:own")
                        .split(body().tokenize(",")).shareUnitOfWork()
                            .process(e -> e.getExchangeExtension().addOnCompletion(new Recorder("part")))
                            .to("seda:line")
                        .end();

                from("direct:line")
                        .choice()
                            .when(body().startsWith("a")).to("seda:line")
                            .when(body().isEqualTo("bad")).throwException(new IllegalArgumentException("bad line"))
                            .otherwise().to("mock:line")
                        .end();

                from("seda:line").routeId("seda")
                        .filter(body().endsWith("fail-later"))
                            .throwException(new IllegalStateException("seda failed"))
                        .end()
                        .to("mock:seda");
            }
        };
    }

    private final class Recorder implements Synchronization {
        private final String name;

        private Recorder(String name) {
            this.name = name;
        }

        @Override
        public void onComplete(Exchange exchange) {
            events.add(name + ":complete:" + exchange.getExchangeId());
        }

        @Override
        public void onFailure(Exchange exchange) {
            events.add(name + ":failure:" + exchange.getExchangeId());
        }
    }
}
