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

import java.util.ArrayList;
import java.util.EventObject;
import java.util.List;

import org.apache.camel.AsyncCallback;
import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.NamedNode;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.event.AbstractExchangeEvent;
import org.apache.camel.model.PipelineDefinition;
import org.apache.camel.spi.InterceptStrategy;
import org.apache.camel.support.processor.DelegateAsyncProcessor;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.util.StopWatch;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test showing how you can use pipeline to group together statistics and implement your own event listener.
 */
public class PipelineStepWithEventTest extends ContextTestSupport {

    private final MyStepEventListener listener = new MyStepEventListener();

    @Test
    public void testPipelineStep() throws Exception {
        getMockEndpoint("mock:a").expectedMessageCount(1);
        getMockEndpoint("mock:a2").expectedMessageCount(1);
        getMockEndpoint("mock:b").expectedMessageCount(1);
        getMockEndpoint("mock:b2").expectedMessageCount(1);
        getMockEndpoint("mock:result").expectedMessageCount(1);

        template.sendBody("direct:start", "Hello World");

        assertMockEndpointsSatisfied();

        assertEquals(4, listener.getEvents().size());

        BeforeStepEvent event = (BeforeStepEvent) listener.getEvents().get(0);
        assertEquals("step-a", event.getId());
        AfterStepEvent event2 = (AfterStepEvent) listener.getEvents().get(1);
        assertEquals("step-a", event2.getId());
        assertTrue(event2.getTimeTaken() > 0, "Should take a little time");
        BeforeStepEvent event3 = (BeforeStepEvent) listener.getEvents().get(2);
        assertEquals("step-b", event3.getId());
        AfterStepEvent event4 = (AfterStepEvent) listener.getEvents().get(3);
        assertEquals("step-b", event4.getId());
        assertTrue(event4.getTimeTaken() > 0, "Should take a little time");
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").pipeline().id("step-a").to("mock:a").delay(constant(10)).end() // a
                        // bit
                        // ugly
                        // by
                        // need
                        // to
                        // end
                        // delay
                        .to("mock:a2").end().pipeline().id("step-b").to("mock:b").delay(constant(20)).end() // a
                        // bit
                        // ugly
                        // by
                        // need
                        // to
                        // end
                        // delay
                        .to("mock:b2").end().to("mock:result");
            }
        };
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        context.getCamelContextExtension().addInterceptStrategy(new MyInterceptStrategy());
        // register the event listener
        context.addService(listener);
        return context;
    }

    private interface StepEventListener {

        void beforeStep(BeforeStepEvent event);

        void afterStep(AfterStepEvent event);

    }

    private static class MyStepEventListener extends ServiceSupport implements StepEventListener {

        private final List<EventObject> events = new ArrayList<>();

        @Override
        public void beforeStep(BeforeStepEvent event) {
            events.add(event);
        }

        @Override
        public void afterStep(AfterStepEvent event) {
            events.add(event);
        }

        public List<EventObject> getEvents() {
            return events;
        }

        @Override
        protected void doStart() throws Exception {
            // noop
        }

        @Override
        protected void doStop() throws Exception {
            // noop
        }
    }

    private static class MyInterceptStrategy implements InterceptStrategy {

        @Override
        public Processor wrapProcessorInInterceptors(
                CamelContext context, NamedNode definition, Processor target, Processor nextTarget)
                throws Exception {
            // grab the listener
            StepEventListener listener = context.hasService(StepEventListener.class);

            // wrap the pipelines so we can emit events
            if (definition instanceof PipelineDefinition) {
                return new MyStepEventProcessor(definition.getId(), target, listener);
            } else {
                return target;
            }
        }
    }

    private static class MyStepEventProcessor extends DelegateAsyncProcessor {

        private final StepEventListener listener;
        private final String id;

        public MyStepEventProcessor(String id, Processor processor, StepEventListener listener) {
            super(processor);
            this.id = id;
            this.listener = listener;
        }

        @Override
        public boolean process(final Exchange exchange, final AsyncCallback callback) {
            final StopWatch watch = new StopWatch();
            if (listener != null) {
                listener.beforeStep(new BeforeStepEvent(exchange, id));
            }
            return super.process(exchange, doneSync -> {
                if (listener != null) {
                    listener.afterStep(new AfterStepEvent(exchange, id, watch.taken()));
                }
                callback.done(doneSync);
            });
        }

    }

    private static class BeforeStepEvent extends AbstractExchangeEvent {

        private final String id;

        public BeforeStepEvent(Exchange source, String id) {
            super(source);
            this.id = id;
        }

        @Override
        public Type getType() {
            return Type.Custom;
        }

        public String getId() {
            return id;
        }
    }

    private static class AfterStepEvent extends AbstractExchangeEvent {

        private final String id;
        private final long timeTaken;

        public AfterStepEvent(Exchange source, String id, long timeTaken) {
            super(source);
            this.id = id;
            this.timeTaken = timeTaken;
        }

        @Override
        public Type getType() {
            return Type.Custom;
        }

        public String getId() {
            return id;
        }

        public long getTimeTaken() {
            return timeTaken;
        }
    }
}
