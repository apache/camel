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

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.SimpleRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * Atom Good Blog repository idempotency test
 */
@DisabledOnOs(OS.AIX)
public class AtomGoodBlogsRepositoryItempotencyTest {

    // This is the CamelContext that is the heart of Camel
    private CamelContext context;

    protected CamelContext createCamelContext() throws Exception {

        // First we register a blog service in our bean registry
        SimpleRegistry registry = new SimpleRegistry();

        // Then we create the camel context with our bean registry
        context = new DefaultCamelContext(registry);

        // Then we add all the routes we need using the route builder DSL syntax
        context.addRoutes(createMyRoutes());

        return context;
    }

    /**
     * This is the route builder where we create our routes using the Camel DSL
     */
    protected RouteBuilder createMyRoutes() {
        return new RouteBuilder() {
            public void configure() {
                // Idempotent using repository strategy in combination of small delay and throttleEntries=false, ensures that we
                // only process each entry once, even though the full blog is read multiple times
                from("atom:file:src/test/data/feed.atom?splitEntries=true&throttleEntries=false&delay=1000&idempotentStrategy=repository")
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

        AtomEndpoint endpoint = context.getEndpoint("atom:feed.atom?idempotentStrategy=repository", AtomEndpoint.class);
        assertInstanceOf(RepositoryGuidIdempotentStrategy.class, endpoint.getIdempotentStrategy());

        // stop Camel after use
        context.stop();
    }

    /**
     * This is the actual junit test method that does the assertion that our routes is working as expected
     */
    @Test
    void testFiltering() throws Exception {
        // create and start Camel
        context = createCamelContext();
        context.start();

        // Get the mock endpoint
        MockEndpoint mock = context.getEndpoint("mock:result", MockEndpoint.class);

        // There should be at least two good blog entries from the feed
        mock.expectedMessageCount(7);

        // Make sure that the component has time for several reads
        Thread.sleep(3000);

        // Assert
        mock.assertIsSatisfied(3000);

        // stop Camel after use
        context.stop();
    }
}
