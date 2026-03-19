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
package org.apache.camel.component.atom;

import org.apache.camel.*;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Atom Good Blog default idempotency test
 */
@DisabledOnOs(OS.AIX)
public class AtomGoodBlogDefaultIdempotencyTest {

    // START SNIPPET: e1

    // This is the CamelContext that is the heart of Camel
    private CamelContext context;
    private static final String ROUTE_ID = "1234";

    protected CamelContext createCamelContext() throws Exception {
        // Create CamelContext and register test routes using the route builder DSL syntax
        context = new DefaultCamelContext();
        context.addRoutes(createMyRoutes());
        return context;
    }

    /**
     * This is the route builder where we create our routes using the Camel DSL
     */
    protected RouteBuilder createMyRoutes() {
        return new RouteBuilder() {
            public void configure() {
                // Idempotent using default strategy in combination of small delay and throttleEntries=false, ensures that we
                // only process each entry once, even though the full blog is read multiple times
                from("atom:file:src/test/data/feed.atom?splitEntries=true&throttleEntries=false&delay=1000").routeId(ROUTE_ID)
                        .to("mock:result");
            }
        };
    }

    /**
     * This test verifies that the correct idempotent strategy is used
     */
    @Test
    void testDefaultIdempotentStrategy() throws Exception {
        // create and start Camel
        context = createCamelContext();

        AtomEndpoint endpoint = context.getEndpoint("atom:*", AtomEndpoint.class);
        assertTrue(endpoint.isIdempotent());
        assertInstanceOf(ItemUpdatedIdempotentStrategy.class, endpoint.getIdempotentStrategy());

        // stop Camel after use
        context.stop();
    }

    @Test
    void testIdempotentFilterFullRead() throws Exception {
        // create and start Camel
        context = createCamelContext();
        context.start();

        // Get the mock endpoint
        MockEndpoint mock = context.getEndpoint("mock:result", MockEndpoint.class);
        Thread.sleep(5000);

        // There should be at least two good blog entries from the feed
        mock.expectedMessageCount(7);

        // Assert
        mock.assertIsSatisfied();

        // stop Camel after use
        context.stop();
    }

    @Test
    void testIdempotentFilterSimulateFeedUpdate() throws Exception {
        // create and start Camel
        context = createCamelContext();
        context.start();

        // Get the mock endpoint
        MockEndpoint mock = context.getEndpoint("mock:result", MockEndpoint.class);

        // Read all 7 items from the feed...
        mock.expectedMessageCount(7);
        mock.assertIsSatisfied();

        // Simulate feed update by resetting the file...
        AtomEntryPollingConsumer atomConsumer = (AtomEntryPollingConsumer) context.getRoute(ROUTE_ID).getConsumer();
        atomConsumer.getEndpoint().setFeedUri("file:src/test/data/updated-feed.atom");

        // Read the three updated entries
        mock.expectedMessageCount(10);
        mock.assertIsSatisfied();

        // stop Camel after use
        context.stop();
    }
    // END SNIPPET: e1

}
