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
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Producer;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.processor.async.MyAsyncComponent;
import org.apache.camel.spi.CamelEvent;
import org.apache.camel.spi.CamelEvent.ExchangeSendingEvent;
import org.apache.camel.spi.CamelEvent.ExchangeSentEvent;
import org.apache.camel.spi.SendHook;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.support.EventNotifierSupport;
import org.apache.camel.support.PluginHelper;
import org.apache.camel.support.service.ServiceHelper;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The {@link SendHook} runs around every send to an endpoint, at each of the places that send (CAMEL-26331).
 */
class SendHookTest extends ContextTestSupport {

    private static final String STOPPED = "Stopped by the send hook";

    // the hook calls and the sending/sent events, in the order they happened
    private final Queue<String> calls = new ConcurrentLinkedQueue<>();
    private final TestHook hook = new TestHook();

    @Override
    protected CamelContext createCamelContext() throws Exception {
        DefaultCamelContext context = (DefaultCamelContext) super.createCamelContext();
        context.getManagementStrategy().addEventNotifier(new EventNotifierSupport() {
            @Override
            public void notify(CamelEvent event) {
                if (event instanceof ExchangeSendingEvent sending) {
                    calls.add("sending:" + sending.getEndpoint().getEndpointUri());
                } else if (event instanceof ExchangeSentEvent sent) {
                    calls.add("sent:" + sent.getEndpoint().getEndpointUri());
                }
            }
        });
        context.getCamelContextExtension().setSendHook(hook);
        return context;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                context.addComponent("async", new MyAsyncComponent());

                from("direct:to").to("mock:a");
                from("direct:toD").toD("mock:${header.target}");
                from("direct:enrich").enrich("direct:resource").to("mock:enriched");
                from("direct:resource").transform(constant("resource"));
                from("direct:wireTap").wireTap("mock:tap").to("mock:afterTap");
                from("direct:recipientList").recipientList(header("to"));
                from("direct:routingSlip").routingSlip(header("slip"));
                from("direct:multicast").multicast().to("mock:m1", "mock:m2");
                from("direct:async").to("async:bye:camel").to("mock:afterAsync");

                from("direct:change").to("mock:change");
                from("direct:reply").to("mock:replyTarget").to("mock:afterReply");
                from("direct:skip").to("mock:skipped").to("mock:afterSkip");
                from("direct:delay").to("mock:delayed").to("mock:afterDelay");

                from("direct:stopTo").errorHandler(deadLetterChannel("mock:dead").maximumRedeliveries(2).redeliveryDelay(0))
                        .to("mock:stopped");
                from("direct:stopRecipients")
                        .errorHandler(deadLetterChannel("mock:dead").maximumRedeliveries(2).redeliveryDelay(0))
                        .recipientList(constant("mock:ok,mock:stopped"));
                from("direct:stopSlip").errorHandler(deadLetterChannel("mock:dead").maximumRedeliveries(2).redeliveryDelay(0))
                        .routingSlip(constant("mock:ok,mock:stopped"));
                from("direct:stopOnException")
                        .onException(IllegalStateException.class).handled(true).to("mock:handled").end()
                        .to("mock:stopped");
                from("direct:stopTry")
                        .doTry().recipientList(constant("mock:stopped")).end().doCatch(IllegalStateException.class)
                        .to("mock:caught").end();
            }
        };
    }

    @Test
    void testTo() throws Exception {
        getMockEndpoint("mock:a").expectedBodiesReceived("Hello");
        template.sendBody("direct:to", "Hello");
        assertMockEndpointsSatisfied();
        assertHookAroundSend("mock://a");
    }

    @Test
    void testToD() throws Exception {
        getMockEndpoint("mock:dyn").expectedBodiesReceived("Hello");
        template.sendBodyAndHeader("direct:toD", "Hello", "target", "dyn");
        assertMockEndpointsSatisfied();
        assertHookAroundSend("mock://dyn");
    }

    @Test
    void testEnrich() throws Exception {
        getMockEndpoint("mock:enriched").expectedBodiesReceived("resource");
        template.sendBody("direct:enrich", "Hello");
        assertMockEndpointsSatisfied();
        assertHookAroundSend("direct://resource");
        assertHookAroundSend("mock://enriched");
    }

    @Test
    void testWireTap() throws Exception {
        getMockEndpoint("mock:tap").expectedBodiesReceived("Hello");
        getMockEndpoint("mock:afterTap").expectedBodiesReceived("Hello");
        template.sendBody("direct:wireTap", "Hello");
        assertMockEndpointsSatisfied();
        // the tap is sent on another thread, and its after hook runs after the mock got the message
        await().atMost(10, TimeUnit.SECONDS).until(() -> calls.contains("sent:mock://tap"));
        assertHookAroundSend("mock://tap");
        assertHookAroundSend("mock://afterTap");
    }

    @Test
    void testRecipientList() throws Exception {
        getMockEndpoint("mock:r1").expectedBodiesReceived("Hello");
        getMockEndpoint("mock:r2").expectedBodiesReceived("Hello");
        template.sendBodyAndHeader("direct:recipientList", "Hello", "to", "mock:r1,mock:r2");
        assertMockEndpointsSatisfied();
        assertHookAroundSend("mock://r1");
        assertHookAroundSend("mock://r2");
    }

    @Test
    void testRoutingSlip() throws Exception {
        getMockEndpoint("mock:s1").expectedBodiesReceived("Hello");
        getMockEndpoint("mock:s2").expectedBodiesReceived("Hello");
        template.sendBodyAndHeader("direct:routingSlip", "Hello", "slip", "mock:s1,mock:s2");
        assertMockEndpointsSatisfied();
        assertHookAroundSend("mock://s1");
        assertHookAroundSend("mock://s2");
    }

    @Test
    void testMulticast() throws Exception {
        getMockEndpoint("mock:m1").expectedBodiesReceived("Hello");
        getMockEndpoint("mock:m2").expectedBodiesReceived("Hello");
        template.sendBody("direct:multicast", "Hello");
        assertMockEndpointsSatisfied();
        assertHookAroundSend("mock://m1");
        assertHookAroundSend("mock://m2");
    }

    @Test
    void testProducerTemplate() throws Exception {
        getMockEndpoint("mock:pt").expectedBodiesReceived("Hello");
        template.sendBody("mock:pt", "Hello");
        assertMockEndpointsSatisfied();
        assertHookAroundSend("mock://pt");
    }

    @Test
    void testProducerTemplateAsync() throws Exception {
        getMockEndpoint("mock:ptAsync").expectedBodiesReceived("Hello");
        template.asyncSendBody("mock:ptAsync", "Hello").get(10, TimeUnit.SECONDS);
        assertMockEndpointsSatisfied();
        // the hook runs once, though this path fires the sending and the sent event twice (as it did before the hook)
        assertEquals(List.of("sending:mock://ptAsync", "sending:mock://ptAsync", "before:mock://ptAsync",
                "after:mock://ptAsync", "sent:mock://ptAsync", "sent:mock://ptAsync"), calls("mock://ptAsync"));
    }

    @Test
    void testDeferredProducer() throws Exception {
        getMockEndpoint("mock:deferred").expectedBodiesReceived("Hello");
        Producer producer = PluginHelper.getDeferServiceFactory(context).createProducer(context.getEndpoint("mock:deferred"));
        ServiceHelper.startService(producer);
        try {
            Exchange exchange = new DefaultExchange(context);
            exchange.getMessage().setBody("Hello");
            producer.process(exchange);
            assertNull(exchange.getException());
        } finally {
            ServiceHelper.stopService(producer);
        }
        assertMockEndpointsSatisfied();
        assertHookAroundSend("mock://deferred");
    }

    @Test
    void testAsyncProducer() throws Exception {
        getMockEndpoint("mock:afterAsync").expectedBodiesReceived("Bye Camel");
        Object reply = template.requestBody("direct:async", "Hello");
        assertEquals("Bye Camel", reply);
        assertMockEndpointsSatisfied();
        assertHookAroundSend("async://bye:camel");
    }

    @Test
    void testChangedMessageReachesProducer() throws Exception {
        getMockEndpoint("mock:change").expectedBodiesReceived("changed");
        template.sendBody("direct:change", "Hello");
        assertMockEndpointsSatisfied();
    }

    @Test
    void testChangedReplyReachesRoute() throws Exception {
        getMockEndpoint("mock:replyTarget").expectedBodiesReceived("Hello");
        getMockEndpoint("mock:afterReply").expectedBodiesReceived("reply changed");
        Object reply = template.requestBody("direct:reply", "Hello");
        assertEquals("reply changed", reply);
        assertMockEndpointsSatisfied();
    }

    @Test
    void testSkip() throws Exception {
        getMockEndpoint("mock:skipped").expectedMessageCount(0);
        getMockEndpoint("mock:afterSkip").expectedBodiesReceived("Hello");
        Exchange out = template.send("direct:skip", e -> e.getMessage().setBody("Hello"));
        assertNull(out.getException());
        assertMockEndpointsSatisfied();
        // the skipped send has its after hook and its events
        assertHookAroundSend("mock://skipped");
    }

    @Test
    void testDelayDoesNotBlockTheCallerThread() throws Exception {
        MockEndpoint delayed = getMockEndpoint("mock:delayed");
        delayed.expectedBodiesReceived("Hello");
        getMockEndpoint("mock:afterDelay").expectedBodiesReceived("Hello");
        List<String> threads = new CopyOnWriteArrayList<>();
        delayed.whenAnyExchangeReceived(e -> threads.add(Thread.currentThread().getName()));

        template.sendBody("direct:delay", "Hello");

        assertMockEndpointsSatisfied();
        assertHookAroundSend("mock://delayed");
        // the producer was called from the scheduled thread pool of the error handler, not from the thread that sent
        assertEquals(1, threads.size());
        assertNotEquals(Thread.currentThread().getName(), threads.get(0));
        assertTrue(threads.get(0).contains("ErrorHandlerRedeliveryTask"), threads.get(0));
    }

    @Test
    void testStoppedSendIsRedeliveredAndDeadLettered() throws Exception {
        getMockEndpoint("mock:stopped").expectedMessageCount(0);
        MockEndpoint dead = getMockEndpoint("mock:dead");
        dead.expectedMessageCount(1);

        template.sendBody("direct:stopTo", "Hello");

        assertMockEndpointsSatisfied();
        assertStoppedAndDeadLettered(dead);
        // the first attempt and 2 redeliveries: the hook stopped each of them
        assertEquals(3, hook.before("mock://stopped"));
        assertEquals(3, hook.after("mock://stopped"));
    }

    @Test
    void testStoppedSendInRecipientListIsRedeliveredPerRecipient() throws Exception {
        // the hook runs inside the error handler of the recipient, so only the stopped recipient is redelivered
        getMockEndpoint("mock:ok").expectedMessageCount(1);
        getMockEndpoint("mock:stopped").expectedMessageCount(0);
        MockEndpoint dead = getMockEndpoint("mock:dead");
        dead.expectedMessageCount(1);

        template.sendBody("direct:stopRecipients", "Hello");

        assertMockEndpointsSatisfied();
        assertStoppedAndDeadLettered(dead);
        assertEquals(3, hook.before("mock://stopped"));
        assertEquals(1, hook.before("mock://ok"));
    }

    @Test
    void testStoppedSendInRoutingSlipIsRedeliveredPerStep() throws Exception {
        // the hook runs inside the error handler of the step, so only the stopped step is redelivered
        getMockEndpoint("mock:ok").expectedMessageCount(1);
        getMockEndpoint("mock:stopped").expectedMessageCount(0);
        MockEndpoint dead = getMockEndpoint("mock:dead");
        dead.expectedMessageCount(1);

        template.sendBody("direct:stopSlip", "Hello");

        assertMockEndpointsSatisfied();
        assertStoppedAndDeadLettered(dead);
        assertEquals(3, hook.before("mock://stopped"));
        assertEquals(1, hook.before("mock://ok"));
    }

    @Test
    void testStoppedSendGoesToOnException() throws Exception {
        getMockEndpoint("mock:stopped").expectedMessageCount(0);
        MockEndpoint handled = getMockEndpoint("mock:handled");
        handled.expectedMessageCount(1);

        Exchange out = template.send("direct:stopOnException", e -> e.getMessage().setBody("Hello"));

        assertNull(out.getException());
        assertMockEndpointsSatisfied();
        Exception caught = handled.getReceivedExchanges().get(0).getProperty(Exchange.EXCEPTION_CAUGHT, Exception.class);
        assertInstanceOf(IllegalStateException.class, caught);
        assertEquals(STOPPED, caught.getMessage());
    }

    @Test
    void testStoppedSendGoesToDoCatch() throws Exception {
        getMockEndpoint("mock:stopped").expectedMessageCount(0);
        getMockEndpoint("mock:caught").expectedMessageCount(1);

        Exchange out = template.send("direct:stopTry", e -> e.getMessage().setBody("Hello"));

        assertNull(out.getException());
        assertMockEndpointsSatisfied();
        assertEquals(1, hook.before("mock://stopped"));
    }

    @Test
    void testStoppedSendFromProducerTemplate() throws Exception {
        getMockEndpoint("mock:stopped").expectedMessageCount(0);

        Exchange out = template.send("mock:stopped", e -> e.getMessage().setBody("Hello"));

        assertMockEndpointsSatisfied();
        assertInstanceOf(IllegalStateException.class, out.getException());
        assertEquals(STOPPED, out.getException().getMessage());
        assertHookAroundSend("mock://stopped");
    }

    @Test
    void testEventsAreTheSameWithoutHook() throws Exception {
        List<String> withHook = runAllPaths();
        // the hook runs between the sending and the sent event of its send
        assertEquals(List.of("sending:mock://a", "before:mock://a", "after:mock://a", "sent:mock://a"),
                calls("mock://a"));

        context.getCamelContextExtension().setSendHook(null);
        assertNull(context.getCamelContextExtension().getSendHook());
        List<String> withoutHook = runAllPaths();

        // the hook adds its calls, and nothing else changes in the events
        List<String> withHookEventsOnly = withHook.stream().filter(c -> c.startsWith("sen")).toList();
        assertEquals(withHookEventsOnly, withoutHook);
        assertTrue(withoutHook.stream().allMatch(c -> c.startsWith("sen")), withoutHook::toString);
    }

    private List<String> runAllPaths() throws Exception {
        calls.clear();
        template.sendBody("direct:to", "Hello");
        template.sendBodyAndHeader("direct:toD", "Hello", "target", "dyn");
        template.sendBody("direct:enrich", "Hello");
        template.sendBodyAndHeader("direct:recipientList", "Hello", "to", "mock:r1,mock:r2");
        template.sendBodyAndHeader("direct:routingSlip", "Hello", "slip", "mock:s1,mock:s2");
        template.sendBody("direct:multicast", "Hello");
        template.sendBody("mock:pt", "Hello");
        template.asyncSendBody("mock:ptAsync", "Hello").get(10, TimeUnit.SECONDS);
        template.requestBody("direct:async", "Hello");
        return new ArrayList<>(calls);
    }

    private void assertStoppedAndDeadLettered(MockEndpoint dead) {
        Exchange deadExchange = dead.getReceivedExchanges().get(0);
        Exception caught = deadExchange.getProperty(Exchange.EXCEPTION_CAUGHT, Exception.class);
        assertNotNull(caught);
        assertInstanceOf(IllegalStateException.class, caught);
        assertEquals(STOPPED, caught.getMessage());
    }

    /**
     * Asserts that each send to the endpoint ran the hook once before and once after, between the sending and the sent
     * event of that send.
     */
    private void assertHookAroundSend(String uri) {
        List<String> list = calls(uri);
        assertEquals(List.of("sending:" + uri, "before:" + uri, "after:" + uri, "sent:" + uri), list, list::toString);
        assertEquals(1, hook.before(uri));
        assertEquals(1, hook.after(uri));
    }

    private List<String> calls(String uri) {
        return calls.stream().filter(c -> c.endsWith(":" + uri)).toList();
    }

    private final class TestHook implements SendHook {

        @Override
        public long beforeSend(Exchange exchange, Endpoint endpoint) {
            String uri = endpoint.getEndpointUri();
            calls.add("before:" + uri);
            switch (uri) {
                case "mock://stopped":
                    exchange.setException(new IllegalStateException(STOPPED));
                    return PROCEED;
                case "mock://change":
                    exchange.getMessage().setBody("changed");
                    return PROCEED;
                case "mock://skipped":
                    return SKIP;
                case "mock://delayed":
                    return 50;
                default:
                    return PROCEED;
            }
        }

        @Override
        public void afterSend(Exchange exchange, Endpoint endpoint) {
            String uri = endpoint.getEndpointUri();
            calls.add("after:" + uri);
            if ("mock://replyTarget".equals(uri)) {
                exchange.getMessage().setBody("reply changed");
            }
        }

        int before(String uri) {
            return (int) calls.stream().filter(c -> c.equals("before:" + uri)).count();
        }

        int after(String uri) {
            return (int) calls.stream().filter(c -> c.equals("after:" + uri)).count();
        }
    }
}
