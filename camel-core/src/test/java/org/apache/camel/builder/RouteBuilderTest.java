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
import java.util.Iterator;
import java.util.List;

import org.apache.camel.CamelContext;
import org.apache.camel.Channel;
import org.apache.camel.DelegateProcessor;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.Route;
import org.apache.camel.TestSupport;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.EventDrivenConsumerRoute;
import org.apache.camel.processor.ChoiceProcessor;
import org.apache.camel.processor.DeadLetterChannel;
import org.apache.camel.processor.EvaluateExpressionProcessor;
import org.apache.camel.processor.FilterProcessor;
import org.apache.camel.processor.MulticastProcessor;
import org.apache.camel.processor.Pipeline;
import org.apache.camel.processor.RecipientList;
import org.apache.camel.processor.SendProcessor;
import org.apache.camel.processor.Splitter;
import org.apache.camel.processor.ThreadsProcessor;
import org.apache.camel.processor.idempotent.IdempotentConsumer;
import org.apache.camel.processor.idempotent.MemoryIdempotentRepository;

/**
 * @version 
 */
public class RouteBuilderTest extends TestSupport {
    protected Processor myProcessor = new MyProcessor();
    protected DelegateProcessor interceptor1;
    protected DelegateProcessor interceptor2;

    protected CamelContext createCamelContext() {
        // disable stream cache otherwise to much hazzle in this unit test to filter the stream cache
        // in all the assertion codes
        DefaultCamelContext ctx = new DefaultCamelContext();
        ctx.setStreamCaching(Boolean.FALSE);
        return ctx;
    }

    protected List<Route> buildSimpleRoute() throws Exception {
        // START SNIPPET: e1
        RouteBuilder builder = new RouteBuilder() {
            public void configure() {
                errorHandler(deadLetterChannel("mock:error"));

                from("direct:a").to("direct:b");
            }
        };
        // END SNIPPET: e1
        return getRouteList(builder);
    }

    public void testSimpleRoute() throws Exception {
        List<Route> routes = buildSimpleRoute();

        assertEquals("Number routes created", 1, routes.size());
        for (Route route : routes) {
            Endpoint key = route.getEndpoint();
            assertEquals("From endpoint", "direct://a", key.getEndpointUri());

            EventDrivenConsumerRoute consumer = assertIsInstanceOf(EventDrivenConsumerRoute.class, route);
            Channel channel = unwrapChannel(consumer.getProcessor());

            SendProcessor sendProcessor = assertIsInstanceOf(SendProcessor.class, channel.getNextProcessor());
            assertEquals("Endpoint URI", "direct://b", sendProcessor.getDestination().getEndpointUri());
        }
    }

    protected List<Route> buildSimpleRouteWithHeaderPredicate() throws Exception {
        // START SNIPPET: e2
        RouteBuilder builder = new RouteBuilder() {
            public void configure() {
                errorHandler(deadLetterChannel("mock:error"));

                from("direct:a")
                    .filter(header("foo").isEqualTo("bar"))
                        .to("direct:b");
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
            assertEquals("From endpoint", "direct://a", key.getEndpointUri());

            EventDrivenConsumerRoute consumer = assertIsInstanceOf(EventDrivenConsumerRoute.class, route);
            Channel channel = unwrapChannel(consumer.getProcessor());

            FilterProcessor filterProcessor = assertIsInstanceOf(FilterProcessor.class, channel.getNextProcessor());
            SendProcessor sendProcessor = assertIsInstanceOf(SendProcessor.class, unwrapChannel(filterProcessor).getNextProcessor());
            assertEquals("Endpoint URI", "direct://b", sendProcessor.getDestination().getEndpointUri());
        }
    }

    protected List<Route> buildSimpleRouteWithChoice() throws Exception {
        // START SNIPPET: e3
        RouteBuilder builder = new RouteBuilder() {
            public void configure() {
                errorHandler(deadLetterChannel("mock:error"));

                from("direct:a")
                    .choice()
                        .when(header("foo").isEqualTo("bar"))
                            .to("direct:b")
                        .when(header("foo").isEqualTo("cheese"))
                            .to("direct:c")
                        .otherwise()
                            .to("direct:d");
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
            assertEquals("From endpoint", "direct://a", key.getEndpointUri());

            EventDrivenConsumerRoute consumer = assertIsInstanceOf(EventDrivenConsumerRoute.class, route);
            Channel channel = unwrapChannel(consumer.getProcessor());

            ChoiceProcessor choiceProcessor = assertIsInstanceOf(ChoiceProcessor.class, channel.getNextProcessor());
            List<FilterProcessor> filters = choiceProcessor.getFilters();
            assertEquals("Should be two when clauses", 2, filters.size());

            Processor filter1 = filters.get(0);
            assertSendTo(unwrapChannel(((FilterProcessor) filter1).getProcessor()).getNextProcessor(), "direct://b");

            Processor filter2 = filters.get(1);
            assertSendTo(unwrapChannel(((FilterProcessor) filter2).getProcessor()).getNextProcessor(), "direct://c");

            assertSendTo(unwrapChannel(choiceProcessor.getOtherwise()).getNextProcessor(), "direct://d");
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
                errorHandler(deadLetterChannel("mock:error"));

                from("direct:a")
                    .process(myProcessor);
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
            assertEquals("From endpoint", "direct://a", key.getEndpointUri());
        }
    }

    protected List<Route> buildCustomProcessorWithFilter() throws Exception {
        // START SNIPPET: e5
        RouteBuilder builder = new RouteBuilder() {
            public void configure() {
                errorHandler(deadLetterChannel("mock:error"));

                from("direct:a")
                    .filter(header("foo").isEqualTo("bar"))
                        .process(myProcessor);
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
            assertEquals("From endpoint", "direct://a", key.getEndpointUri());
        }
    }

    protected List<Route> buildWireTap() throws Exception {
        // START SNIPPET: e6
        RouteBuilder builder = new RouteBuilder() {
            public void configure() {
                errorHandler(deadLetterChannel("mock:error"));

                from("direct:a")
                    .multicast().to("direct:tap", "direct:b");
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
            assertEquals("From endpoint", "direct://a", key.getEndpointUri());

            EventDrivenConsumerRoute consumer = assertIsInstanceOf(EventDrivenConsumerRoute.class, route);
            Channel channel = unwrapChannel(consumer.getProcessor());

            MulticastProcessor multicastProcessor = assertIsInstanceOf(MulticastProcessor.class, channel.getNextProcessor());
            List<Processor> endpoints = new ArrayList<Processor>(multicastProcessor.getProcessors());
            assertEquals("Should have 2 endpoints", 2, endpoints.size());

            assertSendToProcessor(unwrapChannel(endpoints.get(0)).getNextProcessor(), "direct://tap");
            assertSendToProcessor(unwrapChannel(endpoints.get(1)).getNextProcessor(), "direct://b");
        }
    }

    protected List<Route> buildRouteWithInterceptor() throws Exception {
        interceptor1 = new org.apache.camel.processor.DelegateProcessor() {
        };

        interceptor2 = new MyInterceptorProcessor();

        RouteBuilder builder = new RouteBuilder() {
            public void configure() {
                errorHandler(deadLetterChannel("mock:error"));

                from("direct:a")
                    .process(interceptor1)
                    .process(interceptor2)
                    .to("direct:d");
            }
        };
        return getRouteList(builder);
    }

    public void testRouteWithInterceptor() throws Exception {

        List<Route> routes = buildRouteWithInterceptor();

        log.debug("Created routes: " + routes);

        assertEquals("Number routes created", 1, routes.size());
        for (Route route : routes) {
            Endpoint key = route.getEndpoint();
            assertEquals("From endpoint", "direct://a", key.getEndpointUri());

            EventDrivenConsumerRoute consumer = assertIsInstanceOf(EventDrivenConsumerRoute.class, route);

            Pipeline line = assertIsInstanceOf(Pipeline.class, unwrap(consumer.getProcessor()));
            assertEquals(3, line.getProcessors().size());
            // last should be our seda

            List<Processor> processors = new ArrayList<Processor>(line.getProcessors());
            Processor sendTo = assertIsInstanceOf(SendProcessor.class, unwrapChannel(processors.get(2)).getNextProcessor());
            assertSendTo(sendTo, "direct://d");
        }
    }

    public void testComplexExpressions() throws Exception {
        // START SNIPPET: e7
        RouteBuilder builder = new RouteBuilder() {
            public void configure() {
                errorHandler(deadLetterChannel("mock:error"));

                from("direct:a")
                    .filter(header("foo").isEqualTo(123))
                        .to("direct:b");
            }
        };
        // END SNIPPET: e7

        List<Route> routes = getRouteList(builder);
        log.debug("Created routes: " + routes);

        assertEquals("Number routes created", 1, routes.size());
        for (Route route : routes) {
            Endpoint key = route.getEndpoint();
            assertEquals("From endpoint", "direct://a", key.getEndpointUri());
        }
    }

    protected List<Route> buildStaticRecipientList() throws Exception {
        // START SNIPPET: multicast
        RouteBuilder builder = new RouteBuilder() {
            public void configure() {
                errorHandler(deadLetterChannel("mock:error"));

                from("direct:a")
                    .multicast().to("direct:b", "direct:c", "direct:d");
            }
        };
        // END SNIPPET: multicast
        return getRouteList(builder);
    }

    protected List<Route> buildDynamicRecipientList() throws Exception {
        // START SNIPPET: e9
        RouteBuilder builder = new RouteBuilder() {
            public void configure() {
                errorHandler(deadLetterChannel("mock:error"));

                from("direct:a")
                    .recipientList(header("foo"));
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
            assertEquals("From endpoint", "direct://a", key.getEndpointUri());

            EventDrivenConsumerRoute consumer = assertIsInstanceOf(EventDrivenConsumerRoute.class, route);
            Channel channel = unwrapChannel(consumer.getProcessor());

            Pipeline line = assertIsInstanceOf(Pipeline.class, channel.getNextProcessor());
            Iterator<?> it = line.getProcessors().iterator();

            // EvaluateExpressionProcessor should be wrapped in error handler
            Object first = it.next();
            first = assertIsInstanceOf(DeadLetterChannel.class, first).getOutput();
            assertIsInstanceOf(EvaluateExpressionProcessor.class, first);

            // and the second should NOT be wrapped in error handler
            Object second = it.next();
            assertIsInstanceOf(RecipientList.class, second);
        }
    }

    protected List<Route> buildSplitter() throws Exception {
        // START SNIPPET: splitter
        RouteBuilder builder = new RouteBuilder() {
            public void configure() {
                errorHandler(deadLetterChannel("mock:error"));

                from("direct:a")
                    .split(body(String.class).tokenize("\n"))
                        .to("direct:b");
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
            assertEquals("From endpoint", "direct://a", key.getEndpointUri());

            EventDrivenConsumerRoute consumer = assertIsInstanceOf(EventDrivenConsumerRoute.class, route);
            Channel channel = unwrapChannel(consumer.getProcessor());
            assertIsInstanceOf(Splitter.class, channel.getNextProcessor());
        }
    }

    protected List<Route> buildIdempotentConsumer() throws Exception {
        // START SNIPPET: idempotent
        RouteBuilder builder = new RouteBuilder() {
            public void configure() {
                errorHandler(deadLetterChannel("mock:error"));

                from("direct:a")
                    .idempotentConsumer(header("myMessageId"),
                            MemoryIdempotentRepository.memoryIdempotentRepository(200))
                    .to("direct:b");
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
            assertEquals("From endpoint", "direct://a", key.getEndpointUri());

            EventDrivenConsumerRoute consumer = assertIsInstanceOf(EventDrivenConsumerRoute.class, route);
            Channel channel = unwrapChannel(consumer.getProcessor());

            IdempotentConsumer idempotentConsumer = assertIsInstanceOf(IdempotentConsumer.class, channel.getNextProcessor());
            assertEquals("messageIdExpression", "header(myMessageId)", idempotentConsumer.getMessageIdExpression().toString());

            assertIsInstanceOf(MemoryIdempotentRepository.class, idempotentConsumer.getIdempotentRepository());
            SendProcessor sendProcessor = assertIsInstanceOf(SendProcessor.class, unwrapChannel(idempotentConsumer.getProcessor()).getNextProcessor());
            assertEquals("Endpoint URI", "direct://b", sendProcessor.getDestination().getEndpointUri());
        }
    }

    protected List<Route> buildThreads() throws Exception {
        // START SNIPPET: e10
        RouteBuilder builder = new RouteBuilder() {
            public void configure() {
                errorHandler(deadLetterChannel("mock:error"));

                from("direct:a")
                    .threads(5, 10)
                    .to("mock:a")
                    .to("mock:b");
            }
        };
        // END SNIPPET: e10
        return getRouteList(builder);
    }

    public void testThreads() throws Exception {

        List<Route> routes = buildThreads();

        log.debug("Created routes: " + routes);

        assertEquals("Number routes created", 1, routes.size());
        for (Route route : routes) {
            Endpoint key = route.getEndpoint();
            assertEquals("From endpoint", "direct://a", key.getEndpointUri());

            EventDrivenConsumerRoute consumer = assertIsInstanceOf(EventDrivenConsumerRoute.class, route);
            Channel channel = unwrapChannel(consumer.getProcessor());

            Pipeline line = assertIsInstanceOf(Pipeline.class, channel.getNextProcessor());
            Iterator<?> it = line.getProcessors().iterator();

            assertIsInstanceOf(ThreadsProcessor.class, it.next());
            // output should be wrapped in a pipeline
            Pipeline threadsLine = assertIsInstanceOf(Pipeline.class, it.next());
            Iterator<Processor> it2 = threadsLine.getProcessors().iterator();
            assertIsInstanceOf(SendProcessor.class, unwrapChannel(it2.next()).getNextProcessor());
            assertIsInstanceOf(SendProcessor.class, unwrapChannel(it2.next()).getNextProcessor());
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

    protected Processor unwrapDelegateProcessor(Processor processor) {
        if (processor instanceof DelegateProcessor) {
            DelegateProcessor delegate = (DelegateProcessor) processor;
            return delegate.getProcessor();
        } else {
            return processor;
        }
    }

    public void testCorrectNumberOfRoutes() throws Exception {
        RouteBuilder builder = new RouteBuilder() {
            public void configure() throws Exception {
                errorHandler(deadLetterChannel("mock:error"));

                from("direct:start").to("direct:in");

                from("direct:in").to("mock:result");
            }
        };

        List<Route> routes = getRouteList(builder);

        assertEquals(2, routes.size());
    }
}
