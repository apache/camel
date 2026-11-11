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

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.apache.camel.AsyncCallback;
import org.apache.camel.CamelContext;
import org.apache.camel.Channel;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.NamedNode;
import org.apache.camel.Processor;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.sanitizer.Violation;
import org.apache.camel.sanitizer.junit.ExpectSanitizerViolation;
import org.apache.camel.spi.InterceptStrategy;
import org.apache.camel.support.AsyncProcessorSupport;
import org.apache.camel.support.processor.DelegateAsyncProcessor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The {@link InterceptStrategy}s still get the processor of the step as their target when step listeners are installed,
 * and the listeners see the call of the chain of the strategies. With {@code intercept()} and with a strategy that
 * returns a plain {@link Processor}, the listeners see less of the call of the processor, as documented.
 */
class StepListenerInterceptStrategyTest extends ContextTestSupport {

    private final StepListenerRecorder recorder = new StepListenerRecorder();
    // what the strategy got, per node id (the strategies are called on one thread when the routes are created)
    private final Map<String, Processor> targets = Collections.synchronizedMap(new HashMap<>());
    private final Map<String, Processor> nexts = Collections.synchronizedMap(new HashMap<>());
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> new Thread(r, "StepListenerWorker"));
    private final CountDownLatch twiceDone = new CountDownLatch(2);

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        context.getCamelContextExtension().addStepListener(recorder);
        context.getCamelContextExtension().addInterceptStrategy(new TypeCheckingStrategy());
        return context;
    }

    @AfterEach
    void stopWorker() {
        worker.shutdownNow();
    }

    @Override
    protected RoutesBuilder[] createRouteBuilders() {
        return new RoutesBuilder[] { new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:typed").routeId("typed")
                        .to("mock:typed").id("typedTo");

                // the processor calls its callback twice, on the worker thread
                from("direct:twice").routeId("twice")
                        .errorHandler(noErrorHandler())
                        .process(new TwiceDone()).id("twiceStep")
                        .to("mock:twice").id("twiceTo");

                from("direct:plain").routeId("plain")
                        .errorHandler(noErrorHandler())
                        .process(new TwiceDone()).id("plainStep")
                        .to("mock:plain").id("plainTo");
            }
        }, new RouteBuilder() {
            @Override
            public void configure() {
                intercept().to("mock:intercepted").id("interceptTo");

                from("direct:intercepted").routeId("intercepted")
                        .process(e -> e.getMessage().setBody("Bye")).id("interceptedStep");
            }
        } };
    }

    @Test
    void testStrategyGetsTheProcessorOfTheStep() throws Exception {
        Channel channel = StepListenerRecorder.channels(context.getRoute("typed")).get(0);
        // as camel-opentelemetry checks the type of its target
        assertInstanceOf(SendProcessor.class, targets.get("typedTo"));
        assertSame(channel.getNextProcessor(), targets.get("typedTo"));
        assertTrue(nexts.containsKey("typedTo"));
        assertNull(nexts.get("typedTo"));

        getMockEndpoint("mock:typed").expectedMessageCount(1);
        template.sendBody("direct:typed", "Hello");
        assertMockEndpointsSatisfied();

        // the listener sees the call of the chain of the strategy
        assertEquals(List.of("call typed/typedTo", "strategy typed/typedTo", "callback typed/typedTo true",
                "return typed/typedTo true"), recorder.of("typed"));
    }

    @Test
    @ExpectSanitizerViolation(Violation.COMPLETED_TWICE)
    void testSecondCallbackIsSeenWithoutPlainProcessorStrategy() throws Exception {
        getMockEndpoint("mock:twice").expectedMessageCount(1);

        template.sendBody("direct:twice", "Hello");

        assertTrue(twiceDone.await(10, TimeUnit.SECONDS));
        assertMockEndpointsSatisfied();
        List<String> events = recorder.of("twice");
        assertEquals(1, events.stream().filter(e -> e.equals("return twice/twiceStep false")).count(), events.toString());
        assertEquals(2, events.stream().filter(e -> e.equals("callback twice/twiceStep false")).count(),
                events.toString());
    }

    @Test
    void testPlainProcessorStrategyHidesTheSecondCallback() throws Exception {
        getMockEndpoint("mock:plain").expectedMessageCount(1);

        template.sendBody("direct:plain", "Hello");

        assertTrue(twiceDone.await(10, TimeUnit.SECONDS));
        assertMockEndpointsSatisfied();
        // the call waits in the await manager, so the listener sees one synchronous call, and the second callback of
        // the processor reaches the await manager, not the listener
        List<String> events = recorder.of("plain");
        assertEquals(List.of("call plain/plainStep", "callback plain/plainStep true", "return plain/plainStep true"),
                events.subList(0, 3));
        assertEquals(1, events.stream().filter(e -> e.startsWith("callback plain/plainStep ")).count(), events.toString());
    }

    @Test
    void testInterceptHidesTheReturnValueOfTheProcessor() throws Exception {
        getMockEndpoint("mock:intercepted").expectedMessageCount(1);

        Object out = template.requestBody("direct:intercepted", "Hello");

        assertEquals("Bye", out);
        assertMockEndpointsSatisfied();
        // the processor returns true, but the pipeline of intercept() returns false and calls it later; the step of
        // the intercept route is its own call
        assertEquals(List.of(
                "call intercepted/interceptedStep", "return intercepted/interceptedStep false",
                "call intercepted/interceptTo", "callback intercepted/interceptTo true",
                "return intercepted/interceptTo true", "callback intercepted/interceptedStep false"),
                recorder.of("intercepted"));
    }

    /**
     * Calls its callback twice from the worker thread.
     */
    private final class TwiceDone extends AsyncProcessorSupport {
        @Override
        public boolean process(Exchange exchange, AsyncCallback callback) {
            worker.submit(() -> {
                callback.done(false);
                twiceDone.countDown();
                callback.done(false);
                twiceDone.countDown();
            });
            return false;
        }
    }

    /**
     * Wraps the steps whose target is a {@link SendProcessor} in the route typed, as camel-opentelemetry checks the
     * type of its target, and wraps the steps of the route plain in a plain {@link Processor}.
     */
    private final class TypeCheckingStrategy implements InterceptStrategy {
        @Override
        public Processor wrapProcessorInInterceptors(
                CamelContext context, NamedNode definition, Processor target, Processor nextTarget) {
            String id = definition.getId();
            targets.put(id, target);
            nexts.put(id, nextTarget);
            if (id.startsWith("typed") && target instanceof SendProcessor) {
                return new DelegateAsyncProcessor(target) {
                    @Override
                    public boolean process(Exchange exchange, AsyncCallback callback) {
                        recorder.events.add("strategy typed/" + id);
                        return super.process(exchange, callback);
                    }
                };
            }
            if ("plainStep".equals(id)) {
                return target::process;
            }
            return target;
        }
    }
}
