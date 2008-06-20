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
package org.apache.camel.builder;

import java.util.ArrayList;
import java.util.List;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.Route;
import org.apache.camel.TestSupport;
import org.apache.camel.impl.EventDrivenConsumerRoute;
import org.apache.camel.management.InstrumentationProcessor;
import org.apache.camel.management.JmxSystemPropertyKeys;
import org.apache.camel.processor.ChoiceProcessor;
import org.apache.camel.processor.DeadLetterChannel;
import org.apache.camel.processor.DelegateProcessor;
import org.apache.camel.processor.FilterProcessor;
import org.apache.camel.processor.MulticastProcessor;
import org.apache.camel.processor.RecipientList;
import org.apache.camel.processor.SendProcessor;
import org.apache.camel.processor.Splitter;
import org.apache.camel.processor.idempotent.IdempotentConsumer;
import org.apache.camel.processor.idempotent.MemoryMessageIdRepository;
import static org.apache.camel.processor.idempotent.MemoryMessageIdRepository.memoryMessageIdRepository;


/**
 * @version $Revision$
 */
public class RouteBuilderTest extends TestSupport {
    protected Processor myProcessor = new MyProcessor();
    protected DelegateProcessor interceptor1;
    protected DelegateProcessor interceptor2;

    protected List<Route> buildSimpleRoute() throws Exception {
        // START SNIPPET: e1
        RouteBuilder builder = new RouteBuilder() {
            public void configure() {
                from("seda:a").to("seda:b");
            }
        };
        // END SNIPPET: e1
        return getRouteList(builder);
    }

    public void testSimpleRoute() throws Exception {
        List<Route> routes = buildSimpleRoute();

        assertEquals("Number routes created", 1, routes.size());
        for (Route<Exchange> route : routes) {
            Endpoint<Exchange> key = route.getEndpoint();
            assertEquals("From endpoint", "seda:a", key.getEndpointUri());
            Processor processor = getProcessorWithoutErrorHandler(route);
            
            SendProcessor sendProcessor;
            if (Boolean.getBoolean(JmxSystemPropertyKeys.DISABLED)) {
                sendProcessor = assertIsInstanceOf(SendProcessor.class, processor);
            } else {
                InstrumentationProcessor interceptor =
                    assertIsInstanceOf(InstrumentationProcessor.class, processor);
                sendProcessor = assertIsInstanceOf(SendProcessor.class, interceptor.getProcessor());
            }
            assertEquals("Endpoint URI", "seda:b", sendProcessor.getDestination().getEndpointUri());
        }
    }

    protected List<Route> buildSimpleRouteWithHeaderPredicate() throws Exception {
        // START SNIPPET: e2
        RouteBuilder builder = new RouteBuilder() {
            public void configure() {
                from("seda:a").filter(header("foo").isEqualTo("bar")).to("seda:b");
            }
        };
        // END SNIPPET: e2
        return getRouteList(builder);
    }

    public void testSimpleRouteWithHeaderPredicate() throws Exception {
        List<Route> routes = buildSimpleRouteWithHeaderPredicate();

        log.debug("Created routes: " + routes);

        assertEquals("Number routes created", 1, routes.size());
        for (Route route : routes) {
            Endpoint key = route.getEndpoint();
            assertEquals("From endpoint", "seda:a", key.getEndpointUri());
            Processor processor = getProcessorWithoutErrorHandler(route);
            
            if (!Boolean.getBoolean(JmxSystemPropertyKeys.DISABLED)) {
                InstrumentationProcessor interceptor = 
                    assertIsInstanceOf(InstrumentationProcessor.class, processor);
                processor = interceptor.getProcessor();
            }
            
            FilterProcessor filterProcessor = assertIsInstanceOf(FilterProcessor.class, processor);
            SendProcessor sendProcessor = assertIsInstanceOf(SendProcessor.class,
                    unwrapErrorHandler(filterProcessor
                            .getProcessor()));
            assertEquals("Endpoint URI", "seda:b", sendProcessor.getDestination().getEndpointUri());
        }
    }

    protected List<Route> buildSimpleRouteWithChoice() throws Exception {
        // START SNIPPET: e3
        RouteBuilder builder = new RouteBuilder() {
            public void configure() {
                from("seda:a").choice().when(header("foo").isEqualTo("bar")).to("seda:b")
                    .when(header("foo").isEqualTo("cheese")).to("seda:c").otherwise().to("seda:d");
            }
        };
        // END SNIPPET: e3
        return getRouteList(builder);
    }

    public void testSimpleRouteWithChoice() throws Exception {
        List<Route> routes = buildSimpleRouteWithChoice();

        log.debug("Created routes: " + routes);

        assertEquals("Number routes created", 1, routes.size());
        for (Route route : routes) {
            Endpoint key = route.getEndpoint();
            assertEquals("From endpoint", "seda:a", key.getEndpointUri());
            Processor processor = getProcessorWithoutErrorHandler(route);
            
            if (!Boolean.getBoolean(JmxSystemPropertyKeys.DISABLED)) {
                InstrumentationProcessor interceptor = 
                    assertIsInstanceOf(InstrumentationProcessor.class, processor);
                processor = interceptor.getProcessor();
            }

            ChoiceProcessor choiceProcessor = assertIsInstanceOf(ChoiceProcessor.class, processor);
            List<FilterProcessor> filters = choiceProcessor.getFilters();
            assertEquals("Should be two when clauses", 2, filters.size());

            FilterProcessor filter1 = filters.get(0);
            assertSendTo(filter1.getProcessor(), "seda:b");

            FilterProcessor filter2 = filters.get(1);
            assertSendTo(filter2.getProcessor(), "seda:c");

            assertSendTo(choiceProcessor.getOtherwise(), "seda:d");
        }
    }

    protected List<Route> buildCustomProcessor() throws Exception {
        // START SNIPPET: e4
        myProcessor = new Processor() {
            public void process(Exchange exchange) {
                log.debug("Called with exchange: " + exchange);
            }
        };

        RouteBuilder builder = new RouteBuilder() {
            public void configure() {
                from("seda:a").process(myProcessor);
            }
        };
        // END SNIPPET: e4
        return getRouteList(builder);
    }

    public void testCustomProcessor() throws Exception {
        List<Route> routes = buildCustomProcessor();

        assertEquals("Number routes created", 1, routes.size());
        for (Route route : routes) {
            Endpoint key = route.getEndpoint();
            assertEquals("From endpoint", "seda:a", key.getEndpointUri());
            Processor processor = getProcessorWithoutErrorHandler(route);
            if (!Boolean.getBoolean(JmxSystemPropertyKeys.DISABLED)) {
                InstrumentationProcessor interceptor = 
                    assertIsInstanceOf(InstrumentationProcessor.class, processor);
                processor = interceptor.getProcessor();
            }

            assertEquals("Should be called with my processor", myProcessor, processor);
        }
    }

    protected List<Route> buildCustomProcessorWithFilter() throws Exception {
        // START SNIPPET: e5
        RouteBuilder builder = new RouteBuilder() {
            public void configure() {
                from("seda:a").filter(header("foo").isEqualTo("bar")).process(myProcessor);
            }
        };
        // END SNIPPET: e5
        return getRouteList(builder);
    }

    public void testCustomProcessorWithFilter() throws Exception {
        List<Route> routes = buildCustomProcessorWithFilter();

        log.debug("Created routes: " + routes);

        assertEquals("Number routes created", 1, routes.size());
        for (Route route : routes) {
            Endpoint key = route.getEndpoint();
            assertEquals("From endpoint", "seda:a", key.getEndpointUri());
            Processor processor = getProcessorWithoutErrorHandler(route);
            if (!Boolean.getBoolean(JmxSystemPropertyKeys.DISABLED)) {
                InstrumentationProcessor interceptor = 
                    assertIsInstanceOf(InstrumentationProcessor.class, processor);
                processor = interceptor.getProcessor();
            }
            FilterProcessor filterProcessor = assertIsInstanceOf(FilterProcessor.class, processor);
            assertEquals("Should be called with my processor", myProcessor,
                         unwrapErrorHandler(filterProcessor.getProcessor()));
        }
    }

    protected List<Route> buildWireTap() throws Exception {
        // START SNIPPET: e6
        RouteBuilder builder = new RouteBuilder() {
            public void configure() {
                from("seda:a").to("seda:tap", "seda:b");
            }
        };
        // END SNIPPET: e6
        return getRouteList(builder);
    }

    public void testWireTap() throws Exception {
        List<Route> routes = buildWireTap();

        log.debug("Created routes: " + routes);

        assertEquals("Number routes created", 1, routes.size());
        for (Route route : routes) {
            Endpoint key = route.getEndpoint();
            assertEquals("From endpoint", "seda:a", key.getEndpointUri());
            Processor processor = getProcessorWithoutErrorHandler(route);

            MulticastProcessor multicastProcessor = assertIsInstanceOf(MulticastProcessor.class, processor);
            List<Processor> endpoints = new ArrayList<Processor>(multicastProcessor.getProcessors());
            assertEquals("Should have 2 endpoints", 2, endpoints.size());

            assertSendToProcessor(endpoints.get(0), "seda:tap");
            assertSendToProcessor(endpoints.get(1), "seda:b");
        }
    }

    protected List<Route> buildRouteWithInterceptor() throws Exception {
        interceptor1 = new DelegateProcessor() {
        };

        // START SNIPPET: e7
        interceptor2 = new MyInterceptorProcessor();

        RouteBuilder builder = new RouteBuilder() {
            public void configure() {
                from("seda:a").intercept(interceptor1).intercept(interceptor2).to("seda:d");
                /*
                 *
                 * TODO keep old DSL? .intercept() .add(interceptor1)
                 * .add(interceptor2) .target().to("seda:d");
                 */
            }
        };
        // END SNIPPET: e7
        return getRouteList(builder);
    }

    public void testRouteWithInterceptor() throws Exception {

        List<Route> routes = buildRouteWithInterceptor();

        log.debug("Created routes: " + routes);

        assertEquals("Number routes created", 1, routes.size());
        for (Route route : routes) {
            Endpoint key = route.getEndpoint();
            assertEquals("From endpoint", "seda:a", key.getEndpointUri());
            Processor processor = getProcessorWithoutErrorHandler(route);

            if (!Boolean.getBoolean(JmxSystemPropertyKeys.DISABLED)) {
                InstrumentationProcessor interceptor = 
                    assertIsInstanceOf(InstrumentationProcessor.class, processor);
                processor = interceptor.getProcessor();
            }
            
            DelegateProcessor p1 = assertIsInstanceOf(DelegateProcessor.class, processor);
            processor = p1.getProcessor();

            DelegateProcessor p2 = assertIsInstanceOf(DelegateProcessor.class, processor);

            assertSendTo(p2.getProcessor(), "seda:d");
        }
    }

    public void testComplexExpressions() throws Exception {
        // START SNIPPET: e7
        RouteBuilder builder = new RouteBuilder() {
            public void configure() {
                from("seda:a").filter(header("foo").isEqualTo(123)).to("seda:b");
                from("seda:a").filter(header("bar").isGreaterThan(45)).to("seda:b");
            }
        };
        // END SNIPPET: e7

        List<Route> routes = getRouteList(builder);
        log.debug("Created routes: " + routes);

        assertEquals("Number routes created", 2, routes.size());
        for (Route route : routes) {
            Endpoint key = route.getEndpoint();
            assertEquals("From endpoint", "seda:a", key.getEndpointUri());
            Processor processor = getProcessorWithoutErrorHandler(route);

            log.debug("processor: " + processor);
            /*
             * TODO FilterProcessor filterProcessor =
             * assertIsInstanceOf(FilterProcessor.class, processor);
             *
             * SendProcessor sendProcessor =
             * assertIsInstanceOf(SendProcessor.class,
             * filterProcessor.getProcessor()); assertEquals("Endpoint URI",
             * "seda:b", sendProcessor.getDestination().getEndpointUri());
             */
        }
    }

    protected List<Route> buildStaticRecipientList() throws Exception {
        // START SNIPPET: e8
        RouteBuilder builder = new RouteBuilder() {
            public void configure() {
                from("seda:a").to("seda:b", "seda:c", "seda:d");
            }
        };
        // END SNIPPET: e8
        return getRouteList(builder);
    }

    protected List<Route> buildDynamicRecipientList() throws Exception {
        // START SNIPPET: e9
        RouteBuilder builder = new RouteBuilder() {
            public void configure() {
                from("seda:a").recipientList(header("foo"));
            }
        };
        // END SNIPPET: e9
        return getRouteList(builder);
    }

    public void testRouteDynamicReceipentList() throws Exception {

        List<Route> routes = buildDynamicRecipientList();

        log.debug("Created routes: " + routes);

        assertEquals("Number routes created", 1, routes.size());
        for (Route route : routes) {
            Endpoint key = route.getEndpoint();
            assertEquals("From endpoint", "seda:a", key.getEndpointUri());
            Processor processor = getProcessorWithoutErrorHandler(route);
            if (!Boolean.getBoolean(JmxSystemPropertyKeys.DISABLED)) {
                InstrumentationProcessor interceptor = 
                    assertIsInstanceOf(InstrumentationProcessor.class, processor);
                processor = interceptor.getProcessor();
            }
            RecipientList p1 = assertIsInstanceOf(RecipientList.class, processor);
        }
    }

    protected List<Route> buildSplitter() throws Exception {
        // START SNIPPET: splitter
        RouteBuilder builder = new RouteBuilder() {
            public void configure() {
                from("seda:a").splitter(bodyAs(String.class).tokenize("\n")).to("seda:b");
            }
        };
        // END SNIPPET: splitter
        return getRouteList(builder);
    }

    public void testSplitter() throws Exception {

        List<Route> routes = buildSplitter();

        log.debug("Created routes: " + routes);

        assertEquals("Number routes created", 1, routes.size());
        for (Route route : routes) {
            Endpoint key = route.getEndpoint();
            assertEquals("From endpoint", "seda:a", key.getEndpointUri());
            Processor processor = getProcessorWithoutErrorHandler(route);
            if (!Boolean.getBoolean(JmxSystemPropertyKeys.DISABLED)) {
                InstrumentationProcessor interceptor = 
                    assertIsInstanceOf(InstrumentationProcessor.class, processor);
                processor = interceptor.getProcessor();
            }
            Splitter p1 = assertIsInstanceOf(Splitter.class, processor);
        }
    }

    protected List<Route> buildIdempotentConsumer() throws Exception {
        // START SNIPPET: idempotent
        RouteBuilder builder = new RouteBuilder() {
            public void configure() {
                from("seda:a").idempotentConsumer(header("myMessageId"), memoryMessageIdRepository(200))
                    .to("seda:b");
            }
        };
        // END SNIPPET: idempotent
        return getRouteList(builder);
    }

    public void testIdempotentConsumer() throws Exception {

        List<Route> routes = buildIdempotentConsumer();

        log.debug("Created routes: " + routes);

        assertEquals("Number routes created", 1, routes.size());
        for (Route route : routes) {
            Endpoint key = route.getEndpoint();
            assertEquals("From endpoint", "seda:a", key.getEndpointUri());
            Processor processor = getProcessorWithoutErrorHandler(route);
            if (!Boolean.getBoolean(JmxSystemPropertyKeys.DISABLED)) {
                InstrumentationProcessor interceptor = 
                    assertIsInstanceOf(InstrumentationProcessor.class, processor);
                processor = interceptor.getProcessor();
            }
            IdempotentConsumer idempotentConsumer = assertIsInstanceOf(IdempotentConsumer.class, processor);

            assertEquals("messageIdExpression", "header(myMessageId)", idempotentConsumer
                .getMessageIdExpression().toString());

            assertIsInstanceOf(MemoryMessageIdRepository.class, idempotentConsumer.getMessageIdRepository());

            SendProcessor sendProcessor = assertIsInstanceOf(SendProcessor.class,
                                                             unwrapErrorHandler(idempotentConsumer
                                                                 .getNextProcessor()));
            assertEquals("Endpoint URI", "seda:b", sendProcessor.getDestination().getEndpointUri());
        }
    }

    protected void assertSendTo(Processor processor, String uri) {
        if (!(processor instanceof SendProcessor)) {
            processor = unwrapErrorHandler(processor);
        }
        
        SendProcessor sendProcessor = assertIsInstanceOf(SendProcessor.class, processor);
        assertEquals("Endpoint URI", uri, sendProcessor.getDestination().getEndpointUri());
    }

    protected void assertSendToProcessor(Processor processor, String uri) {
        if (!(processor instanceof Producer)) {
            processor = unwrapErrorHandler(processor);
        }
        
        if (!Boolean.getBoolean(JmxSystemPropertyKeys.DISABLED)) {
            InstrumentationProcessor interceptor = 
                assertIsInstanceOf(InstrumentationProcessor.class, processor);
            processor = interceptor.getProcessor();
        }
        
        if (processor instanceof SendProcessor) {
            assertSendTo(processor, uri);
        } else {
            Producer producer = assertIsInstanceOf(Producer.class, processor);
            assertEquals("Endpoint URI", uri, producer.getEndpoint().getEndpointUri());
        }
    }

    /**
     * By default routes should be wrapped in the {@link DeadLetterChannel} so
     * lets unwrap that and return the actual processor
     */
    protected Processor getProcessorWithoutErrorHandler(Route route) {
        EventDrivenConsumerRoute consumerRoute = assertIsInstanceOf(EventDrivenConsumerRoute.class, route);
        Processor processor = unwrap(consumerRoute.getProcessor());
        return unwrapErrorHandler(processor);
    }

    protected Processor unwrapErrorHandler(Processor processor) {
        if (processor instanceof DeadLetterChannel) {
            DeadLetterChannel deadLetter = (DeadLetterChannel)processor;
            return deadLetter.getOutput();
        } else {
            return processor;
        }
    }
}
