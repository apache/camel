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

import java.nio.file.Files;
import java.time.Duration;
import java.util.List;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.AggregationStrategies;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.sanitizer.junit.CamelSanitizer;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Rule R2 with a real consumer that commits with an on completion: the file consumer moves each file to done when its
 * exchange is done. These are common routes on which R2 must report nothing.
 */
@CamelSanitizer
class SanitizerCommitFileTest extends ContextTestSupport {

    private static final String POLL = "?initialDelay=0&delay=10&move=done";

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from(fileUri("split" + POLL)).routeId("split")
                        .split(body().tokenize(","))
                            .to("seda:parts")
                        .end();

                from(fileUri("shared" + POLL)).routeId("shared")
                        .split(body().tokenize(",")).shareUnitOfWork()
                            .to("seda:parts")
                        .end();

                from(fileUri("aggregate" + POLL)).routeId("aggregate")
                        .aggregate(constant(true), AggregationStrategies.groupedBody()).completionSize(2)
                            .to("seda:aggregated")
                        .end();

                from(fileUri("onCompletion" + POLL)).routeId("onCompletion")
                        .onCompletion().to("mock:onCompletion").end()
                        .to("seda:parts");

                from(fileUri("wireTap" + POLL)).routeId("wireTap")
                        .wireTap("seda:tapped")
                        .to("mock:wireTap");

                from(fileUri("pollEnrich" + POLL)).routeId("pollEnrich")
                        .pollEnrich("seda:source", 5000)
                        .to("mock:pollEnrich");

                // the resource of the pollEnrich: a copy that holds the commit of another file, which moves into the
                // unit of work of the exchange that polls it
                from(fileUri("source" + POLL)).routeId("source").to("seda:source");

                from("seda:parts").routeId("parts").to("mock:parts");
                from("seda:aggregated").routeId("aggregated").to("mock:aggregated");
                from("seda:tapped").routeId("tapped").to("mock:tapped");
            }
        };
    }

    @Test
    void aSplitToSeda() throws Exception {
        getMockEndpoint("mock:parts").expectedMessageCount(2);
        drop("split", "a.txt", "a,b");
        assertMockEndpointsSatisfied();
        awaitMoved("split", "a.txt");
        assertNoViolations();
    }

    @Test
    void aSplitWithASharedUnitOfWorkToSeda() throws Exception {
        getMockEndpoint("mock:parts").expectedMessageCount(2);
        drop("shared", "a.txt", "a,b");
        assertMockEndpointsSatisfied();
        awaitMoved("shared", "a.txt");
        assertNoViolations();
    }

    @Test
    void anAggregateToSeda() throws Exception {
        getMockEndpoint("mock:aggregated").expectedMessageCount(1);
        drop("aggregate", "a.txt", "a");
        drop("aggregate", "b.txt", "b");
        assertMockEndpointsSatisfied();
        awaitMoved("aggregate", "a.txt");
        awaitMoved("aggregate", "b.txt");
        assertNoViolations();
    }

    @Test
    void aRouteOnCompletionAndASedaStep() throws Exception {
        getMockEndpoint("mock:parts").expectedMessageCount(1);
        getMockEndpoint("mock:onCompletion").expectedMessageCount(1);
        drop("onCompletion", "a.txt", "a");
        assertMockEndpointsSatisfied();
        awaitMoved("onCompletion", "a.txt");
        assertNoViolations();
    }

    @Test
    void aWireTapToSeda() throws Exception {
        getMockEndpoint("mock:wireTap").expectedMessageCount(1);
        getMockEndpoint("mock:tapped").expectedMessageCount(1);
        drop("wireTap", "a.txt", "a");
        assertMockEndpointsSatisfied();
        awaitMoved("wireTap", "a.txt");
        assertNoViolations();
    }

    @Test
    void aPollEnrichFromSeda() throws Exception {
        getMockEndpoint("mock:pollEnrich").expectedBodiesReceived("resource");
        drop("source", "r.txt", "resource");
        drop("pollEnrich", "a.txt", "a");
        assertMockEndpointsSatisfied();
        awaitMoved("pollEnrich", "a.txt");
        awaitMoved("source", "r.txt");
        assertNoViolations();
    }

    private void drop(String dir, String name, String body) {
        template.sendBodyAndHeader(fileUri(dir), body, Exchange.FILE_NAME, name);
    }

    private void awaitMoved(String dir, String name) {
        // the commit of the file consumer ran: the file is in done, and no longer in the directory polled
        await().atMost(Duration.ofSeconds(10)).until(() -> Files.exists(testFile(dir + "/done/" + name)));
        assertFalse(Files.exists(testFile(dir + "/" + name)));
    }

    private void assertNoViolations() {
        Sanitizer sanitizer = Sanitizer.get(context);
        assertNotNull(sanitizer, "the sanitizer is installed for a test marked with @CamelSanitizer");
        assertEquals(List.of(), sanitizer.getViolations());
    }
}
