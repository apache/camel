/**
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

import org.apache.abdera.model.Entry;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.SimpleRegistry;
import org.apache.camel.test.junit4.TestSupport;
import org.junit.Test;

/**
 * Example for wiki documentation
 */
public class AtomGoodBlogsTest extends TestSupport {

    // START SNIPPET: e1

    // This is the CamelContext that is the heart of Camel
    private CamelContext context;

    protected CamelContext createCamelContext() throws Exception {

        // First we register a blog service in our bean registry
        SimpleRegistry registry = new SimpleRegistry();
        registry.put("blogService", new BlogService());

        // Then we create the camel context with our bean registry
        context = new DefaultCamelContext(registry);

        // Then we add all the routes we need using the route builder DSL syntax
        context.addRoutes(createMyRoutes());

        return context;
    }

    /**
     * This is the route builder where we create our routes using the Camel DSL
     */
    protected RouteBuilder createMyRoutes() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                // We pool the atom feeds from the source for further processing in the seda queue
                // we set the delay to 1 second for each pool as this is a unit test also and we can
                // not wait the default poll interval of 60 seconds.
                // Using splitEntries=true will during polling only fetch one Atom Entry at any given time.
                // As the feed.atom file contains 7 entries, using this will require 7 polls to fetch the entire
                // content. When Camel have reach the end of entries it will refresh the atom feed from URI source
                // and restart - but as Camel by default uses the UpdatedDateFilter it will only deliver new
                // blog entries to "seda:feeds". So only when James Straham updates his blog with a new entry
                // Camel will create an exchange for the seda:feeds.
                from("atom:file:src/test/data/feed.atom?splitEntries=true&consumer.delay=1000").to("seda:feeds");

                // From the feeds we filter each blot entry by using our blog service class
                from("seda:feeds").filter().method("blogService", "isGoodBlog").to("seda:goodBlogs");

                // And the good blogs is moved to a mock queue as this sample is also used for unit testing
                // this is one of the strengths in Camel that you can also use the mock endpoint for your
                // unit tests
                from("seda:goodBlogs").to("mock:result");
            }
        };
    }

    /**
     * This is the actual junit test method that does the assertion that our routes is working as expected
     */
    @Test
    public void testFiltering() throws Exception {
        // create and start Camel
        context = createCamelContext();
        context.start();

        // Get the mock endpoint
        MockEndpoint mock = context.getEndpoint("mock:result", MockEndpoint.class);

        // There should be at least two good blog entries from the feed
        mock.expectedMinimumMessageCount(2);

        // Asserts that the above expectations is true, will throw assertions exception if it failed
        // Camel will default wait max 20 seconds for the assertions to be true, if the conditions
        // is true sooner Camel will continue
        mock.assertIsSatisfied();

        // stop Camel after use
        context.stop();
    }

    /**
     * Services for blogs
     */
    public class BlogService {

        /**
         * Tests the blogs if its a good blog entry or not
         */
        public boolean isGoodBlog(Exchange exchange) {
            Entry entry = exchange.getIn().getBody(Entry.class);
            String title = entry.getTitle();

            // We like blogs about Camel
            boolean good = title.toLowerCase().contains("camel");
            return good;
        }

    }

    // END SNIPPET: e1

}
