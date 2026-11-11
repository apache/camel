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
package org.apache.camel.support;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.apache.camel.AsyncProcessor;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.ExtendedCamelContext;
import org.apache.camel.Ordered;
import org.apache.camel.spi.SendHook;
import org.apache.camel.spi.StepHook;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * More than one {@link StepHook} and {@link SendHook} (CAMEL-26607): the composites the context installs, and how
 * {@link StepHelper} and {@link SendHelper} run them.
 */
class CompositeHookTest extends ContextTestSupport {

    // what the hooks and the producer did, in the order they did it
    private final List<String> calls = new CopyOnWriteArrayList<>();

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    // the step hooks

    @Test
    void testStepHooksInstalledAndRemoved() {
        ExtendedCamelContext ecc = context.getCamelContextExtension();
        assertNull(ecc.getStepHook());

        StepHook a = new TestStepHook("a", 0, PROCEED_STEP);
        StepHook b = new TestStepHook("b", 0, PROCEED_STEP);
        ecc.addStepHook(a);
        assertSame(a, ecc.getStepHook(), "one hook is installed as it is");
        // the same hook again changes nothing
        ecc.addStepHook(a);
        assertSame(a, ecc.getStepHook());

        ecc.addStepHook(b);
        CompositeStepHook composite = assertInstanceOf(CompositeStepHook.class, ecc.getStepHook());
        assertEquals(List.of(a, b), composite.getHooks());

        ecc.removeStepHook(a);
        assertSame(b, ecc.getStepHook());
        // a hook that is not installed is ignored
        ecc.removeStepHook(a);
        assertSame(b, ecc.getStepHook());
        ecc.removeStepHook(b);
        assertNull(ecc.getStepHook());
    }

    @Test
    void testStepHooksAskedInOrder() throws Exception {
        ExtendedCamelContext ecc = context.getCamelContextExtension();
        // added out of order: sorted by Ordered, lower first, a hook without Ordered counts as 0, and hooks with the
        // same order keep the order they were added
        ecc.addStepHook(new TestStepHook("late", 10, PROCEED_STEP));
        ecc.addStepHook(new UnorderedStepHook("plain1"));
        ecc.addStepHook(new TestStepHook("early", -5, PROCEED_STEP));
        ecc.addStepHook(new UnorderedStepHook("plain2"));
        ecc.addStepHook(new TestStepHook("zero", 0, PROCEED_STEP));
        ecc.addStepHook(new TestStepHook("last", Ordered.LOWEST, PROCEED_STEP));

        Exchange exchange = new DefaultExchange(context);
        StepHelper.process(ecc.getStepHook(), exchange, "step1", processor());

        assertEquals(List.of("early:step1", "plain1:step1", "plain2:step1", "zero:step1", "late:step1", "last:step1",
                "processor"), calls);
        assertNull(exchange.getException());
    }

    @Test
    void testStepHookThatThrowsStopsTheChain() throws Exception {
        ExtendedCamelContext ecc = context.getCamelContextExtension();
        ecc.addStepHook(new TestStepHook("first", 1, PROCEED_STEP));
        ecc.addStepHook(new TestStepHook("throws", 2, THROW));
        ecc.addStepHook(new TestStepHook("third", 3, PROCEED_STEP));

        Exchange exchange = new DefaultExchange(context);
        StepHelper.process(ecc.getStepHook(), exchange, "step1", processor());

        // the later hook is not asked, and the processor is not called
        assertEquals(List.of("first:step1", "throws:step1"), calls);
        assertEquals("throws failed the step", exchange.getException().getMessage());
    }

    @Test
    void testStepHookThatSetsAnExceptionStopsTheChain() throws Exception {
        ExtendedCamelContext ecc = context.getCamelContextExtension();
        ecc.addStepHook(new TestStepHook("fails", 1, SET_EXCEPTION));
        ecc.addStepHook(new TestStepHook("second", 2, PROCEED_STEP));

        Exchange exchange = new DefaultExchange(context);
        StepHelper.process(ecc.getStepHook(), exchange, "step1", processor());

        assertEquals(List.of("fails:step1"), calls);
        assertEquals("fails failed the step", exchange.getException().getMessage());
    }

    @Test
    void testStepHookWithReservedValueStopsTheChain() throws Exception {
        ExtendedCamelContext ecc = context.getCamelContextExtension();
        ecc.addStepHook(new TestStepHook("reserved", 1, -7));
        ecc.addStepHook(new TestStepHook("second", 2, PROCEED_STEP));

        Exchange exchange = new DefaultExchange(context);
        StepHelper.process(ecc.getStepHook(), exchange, "step1", processor());

        assertEquals(List.of("reserved:step1"), calls);
        assertInstanceOf(IllegalArgumentException.class, exchange.getException());
    }

    @Test
    void testStepHookDelaysAddUp() {
        StepHook composite = CompositeStepHook.of(List.of(
                new TestStepHook("a", 1, 10),
                new TestStepHook("b", 2, PROCEED_STEP),
                new TestStepHook("c", 3, 25)));

        assertEquals(35, composite.beforeStep(new DefaultExchange(context), "step1"));
        assertEquals(List.of("a:step1", "b:step1", "c:step1"), calls);

        // and do not overflow
        StepHook huge = CompositeStepHook.of(List.of(
                new TestStepHook("x", 1, Long.MAX_VALUE - 1), new TestStepHook("y", 2, 10)));
        assertEquals(Long.MAX_VALUE, huge.beforeStep(new DefaultExchange(context), "step1"));
    }

    @Test
    void testStepHookOfNoneOrOne() {
        assertNull(CompositeStepHook.of(List.of()));
        StepHook one = new TestStepHook("one", 0, PROCEED_STEP);
        assertSame(one, CompositeStepHook.of(List.of(one)));
    }

    // the send hooks

    @Test
    void testSendHooksInstalledAndRemoved() {
        ExtendedCamelContext ecc = context.getCamelContextExtension();
        assertNull(ecc.getSendHook());

        SendHook a = new TestSendHook("a", 0, SendHook.PROCEED);
        SendHook b = new TestSendHook("b", 0, SendHook.PROCEED);
        ecc.addSendHook(a);
        assertSame(a, ecc.getSendHook());
        ecc.addSendHook(a);
        assertSame(a, ecc.getSendHook());
        ecc.addSendHook(b);
        CompositeSendHook composite = assertInstanceOf(CompositeSendHook.class, ecc.getSendHook());
        assertEquals(List.of(a, b), composite.getHooks());

        ecc.removeSendHook(b);
        assertSame(a, ecc.getSendHook());
        ecc.removeSendHook(a);
        assertNull(ecc.getSendHook());
    }

    @Test
    void testSendHooksInOrderAndAfterInReverse() {
        ExtendedCamelContext ecc = context.getCamelContextExtension();
        ecc.addSendHook(new TestSendHook("c", 3, SendHook.PROCEED));
        ecc.addSendHook(new TestSendHook("a", 1, SendHook.PROCEED));
        ecc.addSendHook(new TestSendHook("b", 2, SendHook.PROCEED));

        Exchange exchange = send();

        assertEquals(List.of("before:a", "before:b", "before:c", "producer", "after:c", "after:b", "after:a"), calls);
        assertNull(exchange.getException());
    }

    @Test
    void testFirstSkipWins() {
        ExtendedCamelContext ecc = context.getCamelContextExtension();
        ecc.addSendHook(new TestSendHook("a", 1, SendHook.PROCEED));
        ecc.addSendHook(new TestSendHook("skip", 2, SendHook.SKIP));
        ecc.addSendHook(new TestSendHook("c", 3, SendHook.PROCEED));

        Exchange exchange = send();

        // the later hook is not asked, so it is not called after the send either; the producer is not called
        assertEquals(List.of("before:a", "before:skip", "after:skip", "after:a"), calls);
        assertNull(exchange.getException());
    }

    @Test
    void testSendHookThatThrowsStopsTheChain() {
        ExtendedCamelContext ecc = context.getCamelContextExtension();
        ecc.addSendHook(new TestSendHook("a", 1, SendHook.PROCEED));
        ecc.addSendHook(new TestSendHook("throws", 2, THROW));
        ecc.addSendHook(new TestSendHook("c", 3, SendHook.PROCEED));

        Exchange exchange = send();

        // the hook that threw is not called after the send (its before did not return), the one asked before it is
        assertEquals(List.of("before:a", "before:throws", "after:a"), calls);
        assertEquals("throws stopped the send", exchange.getException().getMessage());
    }

    @Test
    void testSendHookThatSetsAnExceptionStopsTheChain() {
        ExtendedCamelContext ecc = context.getCamelContextExtension();
        ecc.addSendHook(new TestSendHook("a", 1, SendHook.PROCEED));
        ecc.addSendHook(new TestSendHook("stops", 2, SET_EXCEPTION));
        ecc.addSendHook(new TestSendHook("c", 3, SendHook.PROCEED));

        Exchange exchange = send();

        assertEquals(List.of("before:a", "before:stops", "after:stops", "after:a"), calls);
        assertEquals("stops stopped the send", exchange.getException().getMessage());
    }

    @Test
    void testSendHookWithReservedValueStopsTheChain() {
        ExtendedCamelContext ecc = context.getCamelContextExtension();
        ecc.addSendHook(new TestSendHook("a", 1, SendHook.PROCEED));
        ecc.addSendHook(new TestSendHook("reserved", 2, -99));
        ecc.addSendHook(new TestSendHook("c", 3, SendHook.PROCEED));

        Exchange exchange = send();

        assertEquals(List.of("before:a", "before:reserved", "after:reserved", "after:a"), calls);
        assertInstanceOf(IllegalArgumentException.class, exchange.getException());
    }

    @Test
    void testAfterSendThatThrowsDoesNotStopTheOthers() {
        ExtendedCamelContext ecc = context.getCamelContextExtension();
        ecc.addSendHook(new TestSendHook("a", 1, SendHook.PROCEED));
        ecc.addSendHook(new TestSendHook("b", 2, SendHook.PROCEED, true));

        Exchange exchange = send();

        assertEquals(List.of("before:a", "before:b", "producer", "after:b", "after:a"), calls);
        assertEquals("b failed after the send", exchange.getException().getMessage());
    }

    @Test
    void testSendTwiceAsksTheLaterHooksAndSendsTwice() {
        ExtendedCamelContext ecc = context.getCamelContextExtension();
        ecc.addSendHook(new TestSendHook("twice", 1, SendHook.SEND_TWICE));
        ecc.addSendHook(new TestSendHook("c", 2, SendHook.PROCEED));

        Exchange exchange = send();

        // the hooks are asked once and called once after the two sends
        assertEquals(List.of("before:twice", "before:c", "producer", "producer", "after:c", "after:twice"), calls);
        assertNull(exchange.getException());
    }

    @Test
    void testSkipWinsOverSendTwice() {
        ExtendedCamelContext ecc = context.getCamelContextExtension();
        ecc.addSendHook(new TestSendHook("twice", 1, SendHook.SEND_TWICE));
        ecc.addSendHook(new TestSendHook("skip", 2, SendHook.SKIP));

        Exchange exchange = send();

        assertEquals(List.of("before:twice", "before:skip", "after:skip", "after:twice"), calls);
        assertNull(exchange.getException());
    }

    @Test
    void testCompositeCalledDirectlyReturnsSendTwice() {
        SendHook composite = CompositeSendHook.of(List.of(
                new TestSendHook("delay", 1, 30),
                new TestSendHook("twice", 2, SendHook.SEND_TWICE)));

        assertEquals(SendHook.SEND_TWICE, composite.beforeSend(new DefaultExchange(context),
                context.getEndpoint("mock:target")));
    }

    @Test
    void testLongestSendDelayWins() {
        SendHook composite = CompositeSendHook.of(List.of(
                new TestSendHook("a", 1, 10),
                new TestSendHook("b", 2, 40),
                new TestSendHook("c", 3, 20)));
        Endpoint endpoint = context.getEndpoint("mock:target");

        assertEquals(40, composite.beforeSend(new DefaultExchange(context), endpoint));
        assertEquals(List.of("before:a", "before:b", "before:c"), calls);
    }

    @Test
    void testCompositeCalledDirectly() {
        SendHook composite = CompositeSendHook.of(List.of(
                new TestSendHook("a", 1, SendHook.PROCEED),
                new TestSendHook("skip", 2, SendHook.SKIP),
                new TestSendHook("c", 3, SendHook.PROCEED)));
        Endpoint endpoint = context.getEndpoint("mock:target");
        Exchange exchange = new DefaultExchange(context);

        // the first SKIP wins; called directly, afterSend calls every hook in the reverse order
        assertEquals(SendHook.SKIP, composite.beforeSend(exchange, endpoint));
        composite.afterSend(exchange, endpoint);
        assertEquals(List.of("before:a", "before:skip", "after:c", "after:skip", "after:a"), calls);
    }

    @Test
    void testSendHookOfNoneOrOne() {
        assertNull(CompositeSendHook.of(List.of()));
        SendHook one = new TestSendHook("one", 0, SendHook.PROCEED);
        assertSame(one, CompositeSendHook.of(List.of(one)));
    }

    private Exchange send() {
        Endpoint endpoint = context.getEndpoint("mock:target");
        Exchange exchange = new DefaultExchange(context);
        boolean[] done = new boolean[1];
        boolean sync = SendHelper.process(exchange, endpoint, producer(), doneSync -> done[0] = true);
        assertTrue(sync);
        assertTrue(done[0], "the callback is called");
        return exchange;
    }

    private AsyncProcessor processor() {
        return AsyncProcessorConverterHelper.convert(e -> calls.add("processor"));
    }

    private AsyncProcessor producer() {
        return AsyncProcessorConverterHelper.convert(e -> calls.add("producer"));
    }

    // the values a test hook returns that are not a delay
    private static final long PROCEED_STEP = StepHook.PROCEED;
    private static final long THROW = Long.MIN_VALUE;
    private static final long SET_EXCEPTION = Long.MIN_VALUE + 1;

    private final class TestStepHook implements StepHook, Ordered {

        private final String name;
        private final int order;
        private final long answer;

        private TestStepHook(String name, int order, long answer) {
            this.name = name;
            this.order = order;
            this.answer = answer;
        }

        @Override
        public long beforeStep(Exchange exchange, String nodeId) {
            calls.add(name + ":" + nodeId);
            if (answer == THROW) {
                throw new IllegalStateException(name + " failed the step");
            }
            if (answer == SET_EXCEPTION) {
                exchange.setException(new IllegalStateException(name + " failed the step"));
                return PROCEED;
            }
            return answer;
        }

        @Override
        public int getOrder() {
            return order;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    private final class UnorderedStepHook implements StepHook {

        private final String name;

        private UnorderedStepHook(String name) {
            this.name = name;
        }

        @Override
        public long beforeStep(Exchange exchange, String nodeId) {
            calls.add(name + ":" + nodeId);
            return PROCEED;
        }
    }

    private final class TestSendHook implements SendHook, Ordered {

        private final String name;
        private final int order;
        private final long answer;
        private final boolean afterThrows;

        private TestSendHook(String name, int order, long answer) {
            this(name, order, answer, false);
        }

        private TestSendHook(String name, int order, long answer, boolean afterThrows) {
            this.name = name;
            this.order = order;
            this.answer = answer;
            this.afterThrows = afterThrows;
        }

        @Override
        public long beforeSend(Exchange exchange, Endpoint endpoint) {
            calls.add("before:" + name);
            if (answer == THROW) {
                throw new IllegalStateException(name + " stopped the send");
            }
            if (answer == SET_EXCEPTION) {
                exchange.setException(new IllegalStateException(name + " stopped the send"));
                return PROCEED;
            }
            return answer;
        }

        @Override
        public void afterSend(Exchange exchange, Endpoint endpoint) {
            calls.add("after:" + name);
            if (afterThrows) {
                throw new IllegalStateException(name + " failed after the send");
            }
        }

        @Override
        public int getOrder() {
            return order;
        }

        @Override
        public String toString() {
            return name;
        }
    }
}
