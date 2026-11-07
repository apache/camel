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

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Ordered;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spi.CamelEvent;
import org.apache.camel.spi.CamelEvent.ExchangeSendingEvent;
import org.apache.camel.spi.CamelEvent.ExchangeSentEvent;
import org.apache.camel.spi.SendHook;
import org.apache.camel.support.EventNotifierSupport;
import org.apache.camel.support.processor.idempotent.MemoryIdempotentRepository;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * {@link SendHook#SEND_TWICE}: a send that happens twice (CAMEL-26607), as the duplicate fault of camel-chaos.
 */
class SendTwiceTest extends ContextTestSupport {

    // the endpoints the hook sends to twice
    private final Set<String> twice = ConcurrentHashMap.newKeySet();
    // the sending and sent events per endpoint
    private final Map<String, AtomicInteger> sending = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> sent = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> after = new ConcurrentHashMap<>();
    private final AtomicInteger replies = new AtomicInteger();
    private final TwiceHook hook = new TwiceHook();

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        // a stream body stays a stream, so the hook must cache it to send it twice
        context.setStreamCaching(false);
        context.getManagementStrategy().addEventNotifier(new EventNotifierSupport() {
            @Override
            public void notify(CamelEvent event) {
                if (event instanceof ExchangeSendingEvent e) {
                    sending.computeIfAbsent(e.getEndpoint().getEndpointUri(), k -> new AtomicInteger()).incrementAndGet();
                } else if (event instanceof ExchangeSentEvent e) {
                    sent.computeIfAbsent(e.getEndpoint().getEndpointUri(), k -> new AtomicInteger()).incrementAndGet();
                }
            }
        });
        context.getCamelContextExtension().addSendHook(hook);
        return context;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").routeId("start").to("mock:target").to("mock:after");

                from("direct:inout").routeId("inout").to("direct:reply");
                from("direct:reply").routeId("reply")
                        .process(e -> e.getMessage().setBody("reply-" + replies.incrementAndGet()));

                from("direct:fail").routeId("fail").to("mock:fail");

                from("direct:stream").routeId("stream").to("direct:read");
                from("direct:read").routeId("read").convertBodyTo(String.class).to("mock:read");

                from("direct:orders").routeId("orders").to("direct:idempotent");
                from("direct:idempotent").routeId("idempotent")
                        .idempotentConsumer(header("orderId"), MemoryIdempotentRepository.memoryIdempotentRepository())
                        .to("mock:booked");
            }
        };
    }

    @Test
    void testInOnlySendsTwice() throws Exception {
        twice.add("mock://target");
        MockEndpoint target = getMockEndpoint("mock:target");
        target.expectedBodiesReceived("Hello", "Hello");
        target.expectedHeaderValuesReceivedInAnyOrder("foo", "bar", "bar");
        getMockEndpoint("mock:after").expectedBodiesReceived("Hello");

        template.sendBodyAndHeader("direct:start", "Hello", "foo", "bar");

        assertMockEndpointsSatisfied();
        // the events fire once, and the hook is called once after the two sends
        assertEquals(1, sending.get("mock://target").get());
        assertEquals(1, sent.get("mock://target").get());
        assertEquals(1, after.get("mock://target").get());
    }

    @Test
    void testInOutTheSecondReplyWins() {
        twice.add("direct://reply");

        Object reply = template.requestBody("direct:inout", "Hello");

        assertEquals(2, replies.get());
        assertEquals("reply-2", reply);
        assertEquals(1, after.get("direct://reply").get());
    }

    @Test
    void testFailedFirstSendIsNotRepeated() throws Exception {
        twice.add("mock://fail");
        MockEndpoint fail = getMockEndpoint("mock:fail");
        fail.whenAnyExchangeReceived(e -> {
            throw new IllegalStateException("Down");
        });
        fail.expectedMessageCount(1);

        Exchange out = template.send("direct:fail", e -> e.getMessage().setBody("Hello"));

        MockEndpoint.assertIsSatisfied(context);
        assertInstanceOf(IllegalStateException.class, out.getException());
        assertEquals(1, after.get("mock://fail").get());
    }

    @Test
    void testStreamBodyIsSentTwiceIntact() throws Exception {
        twice.add("direct://read");
        getMockEndpoint("mock:read").expectedBodiesReceived("Hello stream", "Hello stream");

        template.sendBody("direct:stream", new ByteArrayInputStream("Hello stream".getBytes(StandardCharsets.UTF_8)));

        assertMockEndpointsSatisfied();
    }

    @Test
    void testIdempotentConsumerDropsTheDuplicate() throws Exception {
        twice.add("direct://idempotent");
        MockEndpoint booked = getMockEndpoint("mock:booked");
        booked.expectedBodiesReceived("order 1", "order 2");

        template.sendBodyAndHeader("direct:orders", "order 1", "orderId", "1");
        template.sendBodyAndHeader("direct:orders", "order 2", "orderId", "2");

        assertMockEndpointsSatisfied();
        // each order reached the idempotent consumer twice
        assertEquals(2, sending.get("direct://idempotent").get());
        assertEquals(2, after.get("direct://idempotent").get());
    }

    @Test
    void testProducerTemplateSendsTwice() throws Exception {
        twice.add("mock://target");
        getMockEndpoint("mock:target").expectedBodiesReceived("Hello", "Hello");

        Exchange out = template.send("mock:target", ExchangePattern.InOnly, e -> e.getMessage().setBody("Hello"));

        assertMockEndpointsSatisfied();
        assertNull(out.getException());
    }

    @Test
    void testSkipOfAnotherHookWins() throws Exception {
        twice.add("mock://target");
        List<String> calls = new CopyOnWriteArrayList<>();
        // asked before the hook that sends twice: it skips the send, so that hook is not asked
        context.getCamelContextExtension().addSendHook(new OrderedHook(-1, SendHook.SKIP, calls));
        getMockEndpoint("mock:target").expectedMessageCount(0);
        getMockEndpoint("mock:after").expectedMessageCount(1);

        template.sendBody("direct:start", "Hello");

        assertMockEndpointsSatisfied();
        assertFalse(after.containsKey("mock://target"));
        assertEquals(List.of("before:mock://target", "after:mock://target", "before:mock://after", "after:mock://after"),
                calls);
    }

    @Test
    void testOtherHooksAreCalledOnceAroundBothSends() throws Exception {
        twice.add("mock://target");
        List<String> calls = new CopyOnWriteArrayList<>();
        // asked after the hook that sends twice, with a delay: the two sends happen, and it is called once after them
        context.getCamelContextExtension().addSendHook(new OrderedHook(Ordered.LOWEST, 1, calls));
        getMockEndpoint("mock:target").expectedBodiesReceived("Hello", "Hello");

        template.sendBody("direct:start", "Hello");

        assertMockEndpointsSatisfied();
        assertEquals(List.of("before:mock://target", "after:mock://target", "before:mock://after", "after:mock://after"),
                calls);
    }

    private final class TwiceHook implements SendHook {

        @Override
        public long beforeSend(Exchange exchange, Endpoint endpoint) {
            return twice.contains(endpoint.getEndpointUri()) ? SEND_TWICE : PROCEED;
        }

        @Override
        public void afterSend(Exchange exchange, Endpoint endpoint) {
            after.computeIfAbsent(endpoint.getEndpointUri(), k -> new AtomicInteger()).incrementAndGet();
        }
    }

    private static final class OrderedHook implements SendHook, Ordered {

        private final int order;
        private final long answer;
        private final List<String> calls;

        private OrderedHook(int order, long answer, List<String> calls) {
            this.order = order;
            this.answer = answer;
            this.calls = calls;
        }

        @Override
        public long beforeSend(Exchange exchange, Endpoint endpoint) {
            if (!endpoint.getEndpointUri().startsWith("mock:")) {
                return PROCEED;
            }
            calls.add("before:" + endpoint.getEndpointUri());
            return "mock://target".equals(endpoint.getEndpointUri()) ? answer : PROCEED;
        }

        @Override
        public void afterSend(Exchange exchange, Endpoint endpoint) {
            if (endpoint.getEndpointUri().startsWith("mock:")) {
                calls.add("after:" + endpoint.getEndpointUri());
            }
        }

        @Override
        public int getOrder() {
            return order;
        }
    }
}
