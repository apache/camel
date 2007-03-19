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
package org.apache.camel;

import junit.framework.TestCase;
import org.apache.camel.builder.RouteBuilder;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @version $Revision$
 */
public class RouteBuilderTest extends TestCase {
    protected Processor<Exchange> myProcessor = new Processor<Exchange>() {
        public void onExchange(Exchange exchange) {
            System.out.println("Called with exchange: " + exchange);
        }

        @Override
        public String toString() {
            return "MyProcessor";
        }
    };

    public void testSimpleRoute() throws Exception {
        // START SNIPPET: e1
        RouteBuilder<Exchange> builder = new RouteBuilder<Exchange>() {
            public void configure() {
                from("seda://a").to("seda://b");
            }
        };
        // END SNIPPET: e1

        Map<Endpoint<Exchange>, Processor<Exchange>> routeMap = builder.getRouteMap();
        Set<Map.Entry<Endpoint<Exchange>, Processor<Exchange>>> routes = routeMap.entrySet();
        assertEquals("Number routes created", 1, routes.size());
        for (Map.Entry<Endpoint<Exchange>, Processor<Exchange>> route : routes) {
            Endpoint<Exchange> key = route.getKey();
            assertEquals("From endpoint", "seda://a", key.getEndpointUri());
            Processor processor = route.getValue();

            assertTrue("Processor should be a SendProcessor but was: " + processor + " with type: " + processor.getClass().getName(), processor instanceof SendProcessor);
            SendProcessor sendProcessor = (SendProcessor) processor;
            assertEquals("Endpoint URI", "seda://b", sendProcessor.getDestination().getEndpointUri());
        }
    }

    public void testSimpleRouteWithHeaderPredicate() throws Exception {
        // START SNIPPET: e2
        RouteBuilder<Exchange> builder = new RouteBuilder<Exchange>() {
            public void configure() {
                from("seda://a").filter(headerEquals("foo", "bar")).to("seda://b");
            }
        };
        // END SNIPPET: e2

        Map<Endpoint<Exchange>, Processor<Exchange>> routeMap = builder.getRouteMap();
        System.out.println("Created map: " + routeMap);

        Set<Map.Entry<Endpoint<Exchange>, Processor<Exchange>>> routes = routeMap.entrySet();
        assertEquals("Number routes created", 1, routes.size());
        for (Map.Entry<Endpoint<Exchange>, Processor<Exchange>> route : routes) {
            Endpoint<Exchange> key = route.getKey();
            assertEquals("From endpoint", "seda://a", key.getEndpointUri());
            Processor processor = route.getValue();

            assertTrue("Processor should be a FilterProcessor but was: " + processor + " with type: " + processor.getClass().getName(), processor instanceof FilterProcessor);
            FilterProcessor filterProcessor = (FilterProcessor) processor;

            SendProcessor sendProcessor = (SendProcessor) filterProcessor.getProcessor();
            assertEquals("Endpoint URI", "seda://b", sendProcessor.getDestination().getEndpointUri());
        }
    }

    public void testSimpleRouteWithChoice() throws Exception {
        // START SNIPPET: e3
        RouteBuilder<Exchange> builder = new RouteBuilder<Exchange>() {
            public void configure() {
                from("seda://a").choice()
                        .when(headerEquals("foo", "bar")).to("seda://b")
                        .when(headerEquals("foo", "cheese")).to("seda://c")
                        .otherwise().to("seda://d");
            }
        };
        // END SNIPPET: e3

        Map<Endpoint<Exchange>, Processor<Exchange>> routeMap = builder.getRouteMap();
        System.out.println("Created map: " + routeMap);

        Set<Map.Entry<Endpoint<Exchange>, Processor<Exchange>>> routes = routeMap.entrySet();
        assertEquals("Number routes created", 1, routes.size());
        for (Map.Entry<Endpoint<Exchange>, Processor<Exchange>> route : routes) {
            Endpoint<Exchange> key = route.getKey();
            assertEquals("From endpoint", "seda://a", key.getEndpointUri());
            Processor processor = route.getValue();

            assertTrue("Processor should be a ChoiceProcessor but was: " + processor + " with type: " + processor.getClass().getName(), processor instanceof ChoiceProcessor);
            ChoiceProcessor<Exchange> choiceProcessor = (ChoiceProcessor<Exchange>) processor;

            List<FilterProcessor<Exchange>> filters = choiceProcessor.getFilters();
            assertEquals("Should be two when clauses", 2, filters.size());

            FilterProcessor<Exchange> filter1 = filters.get(0);
            assertSendTo(filter1.getProcessor(), "seda://b");

            FilterProcessor<Exchange> filter2 = filters.get(1);
            assertSendTo(filter2.getProcessor(), "seda://c");

            assertSendTo(choiceProcessor.getOtherwise(), "seda://d");
        }
    }

    public void testCustomProcessor() throws Exception {
        // START SNIPPET: e4
        RouteBuilder<Exchange> builder = new RouteBuilder<Exchange>() {
            public void configure() {
                from("seda://a").process(myProcessor);
            }
        };
        // END SNIPPET: e4

        Map<Endpoint<Exchange>, Processor<Exchange>> routeMap = builder.getRouteMap();
        System.out.println("Created map: " + routeMap);

        Set<Map.Entry<Endpoint<Exchange>, Processor<Exchange>>> routes = routeMap.entrySet();
        assertEquals("Number routes created", 1, routes.size());
        for (Map.Entry<Endpoint<Exchange>, Processor<Exchange>> route : routes) {
            Endpoint<Exchange> key = route.getKey();
            assertEquals("From endpoint", "seda://a", key.getEndpointUri());
            Processor processor = route.getValue();

            assertEquals("Should be called with my processor", myProcessor, processor);
        }
    }

    public void testCustomProcessorWithFilter() throws Exception {
        // START SNIPPET: e5
        RouteBuilder<Exchange> builder = new RouteBuilder<Exchange>() {
            public void configure() {
                from("seda://a").filter(headerEquals("foo", "bar")).process(myProcessor);
            }
        };
        // END SNIPPET: e5

        Map<Endpoint<Exchange>, Processor<Exchange>> routeMap = builder.getRouteMap();
        System.out.println("Created map: " + routeMap);

        Set<Map.Entry<Endpoint<Exchange>, Processor<Exchange>>> routes = routeMap.entrySet();
        assertEquals("Number routes created", 1, routes.size());
        for (Map.Entry<Endpoint<Exchange>, Processor<Exchange>> route : routes) {
            Endpoint<Exchange> key = route.getKey();
            assertEquals("From endpoint", "seda://a", key.getEndpointUri());
            Processor processor = route.getValue();

            assertTrue("Processor should be a FilterProcessor but was: " + processor + " with type: " + processor.getClass().getName(), processor instanceof FilterProcessor);
            FilterProcessor filterProcessor = (FilterProcessor) processor;
            assertEquals("Should be called with my processor", myProcessor, filterProcessor.getProcessor());
        }
    }

    protected void assertSendTo(Processor processor, String uri) {
        assertTrue("Processor should be a SendProcessor but was: " + processor + " with type: " + processor.getClass().getName(), processor instanceof SendProcessor);

        SendProcessor sendProcessor = (SendProcessor) processor;
        assertEquals("Endpoint URI", uri, sendProcessor.getDestination().getEndpointUri());
    }
}
