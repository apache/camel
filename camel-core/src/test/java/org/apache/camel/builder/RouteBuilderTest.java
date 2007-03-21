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
package org.apache.camel.builder;

import junit.framework.TestCase;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.processor.ChoiceProcessor;
import org.apache.camel.processor.SendProcessor;
import org.apache.camel.processor.FilterProcessor;
import org.apache.camel.processor.InterceptorProcessor;
import org.apache.camel.processor.RecipientList;
import org.apache.camel.processor.Splitter;
import org.apache.camel.processor.DeadLetterChannel;
import org.apache.camel.processor.MulticastProcessor;
import org.apache.camel.TestSupport;
import org.apache.camel.Processor;
import org.apache.camel.Exchange;
import org.apache.camel.Endpoint;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @version $Revision$
 */
public class RouteBuilderTest extends TestSupport {
    protected Processor<Exchange> myProcessor = new Processor<Exchange>() {
        public void onExchange(Exchange exchange) {
            System.out.println("Called with exchange: " + exchange);
        }

        @Override
        public String toString() {
            return "MyProcessor";
        }
    };
	private InterceptorProcessor<Exchange> interceptor1;
	private InterceptorProcessor<Exchange> interceptor2;
    
	protected RouteBuilder<Exchange> buildSimpleRoute() {
		// START SNIPPET: e1
        RouteBuilder<Exchange> builder = new RouteBuilder<Exchange>() {
            public void configure() {
                from("queue:a").to("queue:b");
            }
        };
        // END SNIPPET: e1
		return builder;
	}

	public void testSimpleRoute() throws Exception {
        RouteBuilder<Exchange> builder = buildSimpleRoute();

        Map<Endpoint<Exchange>, Processor<Exchange>> routeMap = builder.getRouteMap();
        Set<Map.Entry<Endpoint<Exchange>, Processor<Exchange>>> routes = routeMap.entrySet();
        assertEquals("Number routes created", 1, routes.size());
        for (Map.Entry<Endpoint<Exchange>, Processor<Exchange>> route : routes) {
            Endpoint<Exchange> key = route.getKey();
            assertEquals("From endpoint", "queue:a", key.getEndpointUri());
            Processor processor = getProcessorWithoutErrorHandler(route);

            SendProcessor sendProcessor = assertIsInstanceOf(SendProcessor.class, processor);
            assertEquals("Endpoint URI", "queue:b", sendProcessor.getDestination().getEndpointUri());
        }
    }

    protected RouteBuilder<Exchange> buildSimpleRouteWithHeaderPredicate() {
		// START SNIPPET: e2
        RouteBuilder<Exchange> builder = new RouteBuilder<Exchange>() {
            public void configure() {
                from("queue:a").filter(header("foo").isEqualTo("bar")).to("queue:b");
            }
        };
        // END SNIPPET: e2
		return builder;
	}

	public void testSimpleRouteWithHeaderPredicate() throws Exception {
        RouteBuilder<Exchange> builder = buildSimpleRouteWithHeaderPredicate();

        Map<Endpoint<Exchange>, Processor<Exchange>> routeMap = builder.getRouteMap();
        System.out.println("Created map: " + routeMap);

        Set<Map.Entry<Endpoint<Exchange>, Processor<Exchange>>> routes = routeMap.entrySet();
        assertEquals("Number routes created", 1, routes.size());
        for (Map.Entry<Endpoint<Exchange>, Processor<Exchange>> route : routes) {
            Endpoint<Exchange> key = route.getKey();
            assertEquals("From endpoint", "queue:a", key.getEndpointUri());
            Processor processor = getProcessorWithoutErrorHandler(route);

            FilterProcessor filterProcessor = assertIsInstanceOf(FilterProcessor.class, processor);
    SendProcessor sendProcessor = assertIsInstanceOf(SendProcessor.class, unwrapErrorHandler(filterProcessor.getProcessor()));
            assertEquals("Endpoint URI", "queue:b", sendProcessor.getDestination().getEndpointUri());
        }
    }


    protected RouteBuilder<Exchange> buildSimpleRouteWithChoice() {
		// START SNIPPET: e3
        RouteBuilder<Exchange> builder = new RouteBuilder<Exchange>() {
            public void configure() {
                from("queue:a").choice()
                        .when(header("foo").isEqualTo("bar")).to("queue:b")
                        .when(header("foo").isEqualTo("cheese")).to("queue:c")
                        .otherwise().to("queue:d");
            }
        };
        // END SNIPPET: e3
		return builder;
	}

    public void testSimpleRouteWithChoice() throws Exception {
        RouteBuilder<Exchange> builder = buildSimpleRouteWithChoice();

        Map<Endpoint<Exchange>, Processor<Exchange>> routeMap = builder.getRouteMap();
        System.out.println("Created map: " + routeMap);

        Set<Map.Entry<Endpoint<Exchange>, Processor<Exchange>>> routes = routeMap.entrySet();
        assertEquals("Number routes created", 1, routes.size());
        for (Map.Entry<Endpoint<Exchange>, Processor<Exchange>> route : routes) {
            Endpoint<Exchange> key = route.getKey();
            assertEquals("From endpoint", "queue:a", key.getEndpointUri());
            Processor processor = getProcessorWithoutErrorHandler(route);

            ChoiceProcessor<Exchange> choiceProcessor = assertIsInstanceOf(ChoiceProcessor.class, processor);
            List<FilterProcessor<Exchange>> filters = choiceProcessor.getFilters();
            assertEquals("Should be two when clauses", 2, filters.size());

            FilterProcessor<Exchange> filter1 = filters.get(0);
            assertSendTo(filter1.getProcessor(), "queue:b");

            FilterProcessor<Exchange> filter2 = filters.get(1);
            assertSendTo(filter2.getProcessor(), "queue:c");

            assertSendTo(choiceProcessor.getOtherwise(), "queue:d");
        }
    }

    protected RouteBuilder<Exchange> buildCustomProcessor() {
		// START SNIPPET: e4
        myProcessor = new Processor<Exchange>() {
            public void onExchange(Exchange exchange) {
                System.out.println("Called with exchange: " + exchange);
            }
        };

        RouteBuilder<Exchange> builder = new RouteBuilder<Exchange>() {
            public void configure() {
                from("queue:a").process(myProcessor);
            }
        };
        // END SNIPPET: e4
		return builder;
	}

	public void testCustomProcessor() throws Exception {
        RouteBuilder<Exchange> builder = buildCustomProcessor();

        Map<Endpoint<Exchange>, Processor<Exchange>> routeMap = builder.getRouteMap();

        Set<Map.Entry<Endpoint<Exchange>, Processor<Exchange>>> routes = routeMap.entrySet();
        assertEquals("Number routes created", 1, routes.size());
        for (Map.Entry<Endpoint<Exchange>, Processor<Exchange>> route : routes) {
            Endpoint<Exchange> key = route.getKey();
            assertEquals("From endpoint", "queue:a", key.getEndpointUri());
            Processor processor = getProcessorWithoutErrorHandler(route);

            assertEquals("Should be called with my processor", myProcessor, processor);
        }
    }


	protected RouteBuilder<Exchange> buildCustomProcessorWithFilter() {
		// START SNIPPET: e5
        RouteBuilder<Exchange> builder = new RouteBuilder<Exchange>() {
            public void configure() {
                from("queue:a").filter(header("foo").isEqualTo("bar")).process(myProcessor);
            }
        };
        // END SNIPPET: e5
		return builder;
	}

	public void testCustomProcessorWithFilter() throws Exception {
        RouteBuilder<Exchange> builder = buildCustomProcessorWithFilter();

        Map<Endpoint<Exchange>, Processor<Exchange>> routeMap = builder.getRouteMap();
        System.out.println("Created map: " + routeMap);

        Set<Map.Entry<Endpoint<Exchange>, Processor<Exchange>>> routes = routeMap.entrySet();
        assertEquals("Number routes created", 1, routes.size());
        for (Map.Entry<Endpoint<Exchange>, Processor<Exchange>> route : routes) {
            Endpoint<Exchange> key = route.getKey();
            assertEquals("From endpoint", "queue:a", key.getEndpointUri());
            Processor processor = getProcessorWithoutErrorHandler(route);

            FilterProcessor filterProcessor = assertIsInstanceOf(FilterProcessor.class, processor);
            assertEquals("Should be called with my processor", myProcessor, unwrapErrorHandler(filterProcessor.getProcessor()));
        }
    }


	protected RouteBuilder<Exchange> buildWireTap() {
		// START SNIPPET: e6
        RouteBuilder<Exchange> builder = new RouteBuilder<Exchange>() {
            public void configure() {
                from("queue:a").to("queue:tap", "queue:b");
            }
        };
        // END SNIPPET: e6
		return builder;
	}

    public void testWireTap() throws Exception {
        RouteBuilder<Exchange> builder = buildWireTap();

        Map<Endpoint<Exchange>, Processor<Exchange>> routeMap = builder.getRouteMap();
        System.out.println("Created map: " + routeMap);

        Set<Map.Entry<Endpoint<Exchange>, Processor<Exchange>>> routes = routeMap.entrySet();
        assertEquals("Number routes created", 1, routes.size());
        for (Map.Entry<Endpoint<Exchange>, Processor<Exchange>> route : routes) {
            Endpoint<Exchange> key = route.getKey();
            assertEquals("From endpoint", "queue:a", key.getEndpointUri());
            Processor processor = getProcessorWithoutErrorHandler(route);

            MulticastProcessor<Exchange> multicastProcessor = assertIsInstanceOf(MulticastProcessor.class, processor);
            List<Endpoint<Exchange>> endpoints = new ArrayList<Endpoint<Exchange>>(multicastProcessor.getEndpoints());
            assertEquals("Should have 2 endpoints", 2, endpoints.size());

            assertEndpointUri(endpoints.get(0), "queue:tap");
            assertEndpointUri(endpoints.get(1), "queue:b");
        }
    }

    protected RouteBuilder<Exchange> buildRouteWithInterceptor() {
		interceptor1 = new InterceptorProcessor<Exchange>() {
        };

        // START SNIPPET: e7        
        interceptor2 = new InterceptorProcessor<Exchange>() {
        	public void onExchange(Exchange exchange) {
        		System.out.println("START of onExchange: "+exchange);
        		next.onExchange(exchange);
        		System.out.println("END of onExchange: "+exchange);
        	}
        };

        RouteBuilder<Exchange> builder = new RouteBuilder<Exchange>() {
            public void configure() {
                from("queue:a")
                    .intercept()
            		   .add(interceptor1)
            		   .add(interceptor2)
            		.target().to("queue:d");
            }
        };
        // END SNIPPET: e7
		return builder;
	}

    public void testRouteWithInterceptor() throws Exception {
    	
        RouteBuilder<Exchange> builder = buildRouteWithInterceptor();

        Map<Endpoint<Exchange>, Processor<Exchange>> routeMap = builder.getRouteMap();
        System.out.println("Created map: " + routeMap);

        Set<Map.Entry<Endpoint<Exchange>, Processor<Exchange>>> routes = routeMap.entrySet();
        assertEquals("Number routes created", 1, routes.size());
        for (Map.Entry<Endpoint<Exchange>, Processor<Exchange>> route : routes) {
            Endpoint<Exchange> key = route.getKey();
            assertEquals("From endpoint", "queue:a", key.getEndpointUri());
            Processor processor = getProcessorWithoutErrorHandler(route);

            InterceptorProcessor<Exchange> p1 = assertIsInstanceOf(InterceptorProcessor.class, processor);
            processor = p1.getNext();

            InterceptorProcessor<Exchange> p2 = assertIsInstanceOf(InterceptorProcessor.class, processor);

            assertSendTo(p2.getNext(), "queue:d");
        }
    }

	public void testComplexExpressions() throws Exception {
		// START SNIPPET: e7
        RouteBuilder<Exchange> builder = new RouteBuilder<Exchange>() {
            public void configure() {
                from("queue:a").filter(header("foo").isEqualTo(123)).to("queue:b");
                from("queue:a").filter(header("bar").isGreaterThan(45)).to("queue:b");
            }
        };
        // END SNIPPET: e7

        Map<Endpoint<Exchange>, Processor<Exchange>> routeMap = builder.getRouteMap();
        System.out.println("Created map: " + routeMap);

        Set<Map.Entry<Endpoint<Exchange>, Processor<Exchange>>> routes = routeMap.entrySet();
        assertEquals("Number routes created", 1, routes.size());
        for (Map.Entry<Endpoint<Exchange>, Processor<Exchange>> route : routes) {
            Endpoint<Exchange> key = route.getKey();
            assertEquals("From endpoint", "queue:a", key.getEndpointUri());
            Processor processor = getProcessorWithoutErrorHandler(route);

            System.out.println("processor: " + processor);
            /* TODO
            FilterProcessor filterProcessor = assertIsInstanceOf(FilterProcessor.class, processor);

            SendProcessor sendProcessor = assertIsInstanceOf(SendProcessor.class, filterProcessor.getProcessor());
            assertEquals("Endpoint URI", "queue:b", sendProcessor.getDestination().getEndpointUri());
            */
        }
    }

    protected RouteBuilder<Exchange> buildStaticRecipientList() {
        // START SNIPPET: e8
        RouteBuilder<Exchange> builder = new RouteBuilder<Exchange>() {
            public void configure() {
                from("queue:a").to("queue:b", "queue:c", "queue:d");
            }
        };
        // END SNIPPET: e8
        return builder;
    }

    protected RouteBuilder<Exchange> buildDynamicRecipientList() {
        // START SNIPPET: e9
        RouteBuilder<Exchange> builder = new RouteBuilder<Exchange>() {
            public void configure() {
                from("queue:a").recipientList(header("foo"));
            }
        };
        // END SNIPPET: e9
        return builder;
    }

    public void testRouteDynamicReceipentList() throws Exception {

        RouteBuilder<Exchange> builder = buildDynamicRecipientList();

        Map<Endpoint<Exchange>, Processor<Exchange>> routeMap = builder.getRouteMap();
        System.out.println("Created map: " + routeMap);

        Set<Map.Entry<Endpoint<Exchange>, Processor<Exchange>>> routes = routeMap.entrySet();
        assertEquals("Number routes created", 1, routes.size());
        for (Map.Entry<Endpoint<Exchange>, Processor<Exchange>> route : routes) {
            Endpoint<Exchange> key = route.getKey();
            assertEquals("From endpoint", "queue:a", key.getEndpointUri());
            Processor processor = getProcessorWithoutErrorHandler(route);

            RecipientList<Exchange> p1 = assertIsInstanceOf(RecipientList.class, processor);
        }
    }
    protected RouteBuilder<Exchange> buildSplitter() {
        // START SNIPPET: splitter
        RouteBuilder<Exchange> builder = new RouteBuilder<Exchange>() {
            public void configure() {
                from("queue:a").splitter(bodyAs(String.class).tokenize("\n")).to("queue:b");
            }
        };
        // END SNIPPET: splitter
        return builder;
    }

    public void testSplitter() throws Exception {

        RouteBuilder<Exchange> builder = buildSplitter();

        Map<Endpoint<Exchange>, Processor<Exchange>> routeMap = builder.getRouteMap();
        System.out.println("Created map: " + routeMap);

        Set<Map.Entry<Endpoint<Exchange>, Processor<Exchange>>> routes = routeMap.entrySet();
        assertEquals("Number routes created", 1, routes.size());
        for (Map.Entry<Endpoint<Exchange>, Processor<Exchange>> route : routes) {
            Endpoint<Exchange> key = route.getKey();
            assertEquals("From endpoint", "queue:a", key.getEndpointUri());
            Processor processor = getProcessorWithoutErrorHandler(route);

            Splitter<Exchange> p1 = assertIsInstanceOf(Splitter.class, processor);
        }
    }

    protected void assertSendTo(Processor processor, String uri) {
        processor = unwrapErrorHandler(processor);

        SendProcessor sendProcessor = assertIsInstanceOf(SendProcessor.class, processor);
        assertEquals("Endpoint URI", uri, sendProcessor.getDestination().getEndpointUri());
    }

    /**
     * By default routes should be wrapped in the {@link DeadLetterChannel} so lets unwrap that and return the actual processor
     */
    protected Processor<Exchange> getProcessorWithoutErrorHandler(Map.Entry<Endpoint<Exchange>, Processor<Exchange>> route) {
        Processor<Exchange> processor = route.getValue();
        return unwrapErrorHandler(processor);
    }

    protected Processor<Exchange> unwrapErrorHandler(Processor<Exchange> processor) {
        assertTrue("Processor should be a DeadLetterChannel but was: " + processor + " with type: " + processor.getClass().getName(), processor instanceof DeadLetterChannel);
        DeadLetterChannel deadLetter = (DeadLetterChannel) processor;
        return deadLetter.getOutput();
    }

    protected void assertEndpointUri(Endpoint<Exchange> endpoint, String uri) {
        assertEquals("Endoint uri for: " + endpoint, uri, endpoint.getEndpointUri());
    }
}
