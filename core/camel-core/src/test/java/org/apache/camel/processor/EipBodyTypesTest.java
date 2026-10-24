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

import java.io.File;
import java.io.InputStream;
import java.io.Reader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.NamedNode;
import org.apache.camel.Processor;
import org.apache.camel.Route;
import org.apache.camel.StreamCache;
import org.apache.camel.builder.AggregationStrategies;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.AggregateDefinition;
import org.apache.camel.model.CacheDefinition;
import org.apache.camel.model.CatchDefinition;
import org.apache.camel.model.ChoiceDefinition;
import org.apache.camel.model.ClaimCheckDefinition;
import org.apache.camel.model.ClaimCheckOperation;
import org.apache.camel.model.ConvertBodyDefinition;
import org.apache.camel.model.ConvertHeaderDefinition;
import org.apache.camel.model.ConvertVariableDefinition;
import org.apache.camel.model.DelayDefinition;
import org.apache.camel.model.FilterDefinition;
import org.apache.camel.model.IdempotentConsumerDefinition;
import org.apache.camel.model.LoadBalanceDefinition;
import org.apache.camel.model.LogDefinition;
import org.apache.camel.model.LoopDefinition;
import org.apache.camel.model.MulticastDefinition;
import org.apache.camel.model.PipelineDefinition;
import org.apache.camel.model.PolicyDefinition;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.model.RemoveHeaderDefinition;
import org.apache.camel.model.RemoveHeadersDefinition;
import org.apache.camel.model.RemovePropertiesDefinition;
import org.apache.camel.model.RemovePropertyDefinition;
import org.apache.camel.model.RemoveVariableDefinition;
import org.apache.camel.model.RollbackDefinition;
import org.apache.camel.model.SamplingDefinition;
import org.apache.camel.model.SetBodyDefinition;
import org.apache.camel.model.SetHeaderDefinition;
import org.apache.camel.model.SetHeadersDefinition;
import org.apache.camel.model.SetPropertyDefinition;
import org.apache.camel.model.SetVariableDefinition;
import org.apache.camel.model.SetVariablesDefinition;
import org.apache.camel.model.SortDefinition;
import org.apache.camel.model.SplitDefinition;
import org.apache.camel.model.StepDefinition;
import org.apache.camel.model.StopDefinition;
import org.apache.camel.model.ThreadsDefinition;
import org.apache.camel.model.ThrottleDefinition;
import org.apache.camel.model.ThrowExceptionDefinition;
import org.apache.camel.model.TransformDefinition;
import org.apache.camel.model.TryDefinition;
import org.apache.camel.model.ValidateDefinition;
import org.apache.camel.model.WireTapDefinition;
import org.apache.camel.spi.BodyType;
import org.apache.camel.spi.BodyType.Carrier;
import org.apache.camel.spi.BodyTypes;
import org.apache.camel.spi.Policy;
import org.apache.camel.support.processor.idempotent.MemoryIdempotentRepository;
import org.junit.jupiter.api.Test;

import static org.apache.camel.builder.Builder.body;
import static org.apache.camel.builder.Builder.constant;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The body types of the EIPs (CAMEL-25928), checked at runtime: a route for each EIP implemented in core sends a body
 * through it, and the body after it and at the first of its own steps must be what the {@link BodyTypes} of its model
 * class say. The EIPs implemented in other modules, such as the circuit breakers, are checked there.
 */
public class EipBodyTypesTest extends ContextTestSupport {

    /** The body the steps of a block end with. */
    private static final String END = "end of the steps";

    private final List<Object> inside = new CopyOnWriteArrayList<>();
    private int routes;

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    public void theBodyStaysAsItWas() throws Exception {
        unchanged(SetHeaderDefinition.class, r -> r.setHeader("a", constant("b")));
        unchanged(SetHeadersDefinition.class, r -> r.setHeaders("a", constant("b")));
        unchanged(SetPropertyDefinition.class, r -> r.setProperty("a", constant("b")));
        unchanged(SetVariableDefinition.class, r -> r.setVariable("a", constant("b")));
        unchanged(SetVariablesDefinition.class, r -> r.setVariables("a", constant("b")));
        unchanged(RemoveHeaderDefinition.class, r -> r.setHeader("a", constant("b")).removeHeader("a"));
        unchanged(RemoveHeadersDefinition.class, r -> r.removeHeaders("*"));
        unchanged(RemovePropertyDefinition.class, r -> r.removeProperty("a"));
        unchanged(RemovePropertiesDefinition.class, r -> r.removeProperties("a*"));
        unchanged(RemoveVariableDefinition.class, r -> r.removeVariable("a"));
        unchanged(ConvertHeaderDefinition.class, r -> r.setHeader("a", constant(1)).convertHeaderTo("a", String.class));
        unchanged(ConvertVariableDefinition.class,
                r -> r.setVariable("a", constant(1)).convertVariableTo("a", String.class));
        unchanged(LogDefinition.class, r -> r.log("${body}"));
        unchanged(ValidateDefinition.class, r -> r.validate(constant(true)));
        unchanged(DelayDefinition.class, r -> r.delay(1));
        unchanged(ThrottleDefinition.class, r -> r.throttle(10));
        unchanged(SamplingDefinition.class, r -> r.sample());
        unchanged(WireTapDefinition.class, r -> r.wireTap("mock:tap"));
        unchanged(ThreadsDefinition.class, r -> r.threads());
        unchanged(StopDefinition.class, r -> r.stop());
        unchanged(RollbackDefinition.class, r -> r.rollback());
        unchanged(ThrowExceptionDefinition.class, r -> r.throwException(new IllegalArgumentException("Forced")));
    }

    @Test
    public void theBodyIsTheResultOfTheExpression() throws Exception {
        assertFrom(SetBodyDefinition.class, BodyTypeSides.EXPRESSION_OUT);
        assertEquals("Hello", run(r -> r.setBody(constant("Hello")), new Order()).getMessage().getBody());
        assertFrom(TransformDefinition.class, BodyTypeSides.EXPRESSION_OUT);
        assertEquals("Hello", run(r -> r.transform(constant("Hello")), new Order()).getMessage().getBody());
    }

    @Test
    public void theStepsOfABlock() throws Exception {
        block(ChoiceDefinition.class, (r, s) -> r.choice().when(constant(true)).process(s).setBody(constant(END)).end());
        block(FilterDefinition.class, (r, s) -> r.filter(constant(true)).process(s).setBody(constant(END)).end());
        block(TryDefinition.class,
                (r, s) -> r.doTry().process(s).setBody(constant(END)).doFinally().setHeader("a", constant("b")).end());
        block(StepDefinition.class, (r, s) -> r.step("a").process(s).setBody(constant(END)).end());
        block(PipelineDefinition.class, (r, s) -> r.pipeline().process(s).setBody(constant(END)).end());
        block(IdempotentConsumerDefinition.class,
                (r, s) -> r.idempotentConsumer(constant("a"), MemoryIdempotentRepository.memoryIdempotentRepository())
                        .process(s).setBody(constant(END)).end());
        block(LoadBalanceDefinition.class,
                (r, s) -> r.loadBalance().roundRobin().pipeline().process(s).setBody(constant(END)).end().end());
        block(CacheDefinition.class, (r, s) -> r.cache(constant("a")).process(s).setBody(constant(END)).end());
        block(PolicyDefinition.class, (r, s) -> r.policy(new NoPolicy()).process(s).setBody(constant(END)).end());
        block(LoopDefinition.class, (r, s) -> r.loop(1).process(s).setBody(constant(END)).end());
        block(MulticastDefinition.class,
                (r, s) -> r.multicast().pipeline().process(s).setBody(constant(END)).end().end());
    }

    @Test
    public void theBodyAfterDoCatchIsWhatItsStepsEndWith() throws Exception {
        assertFrom(CatchDefinition.class, BodyTypeSides.STEPS);
        Exchange out = run(r -> r.doTry().throwException(new IllegalArgumentException("Forced"))
                .doCatch(IllegalArgumentException.class).setBody(constant(END)).end(), new Order());
        assertEquals(END, out.getMessage().getBody());
    }

    @Test
    public void splitGivesItsStepsEachPartAndKeepsTheBody() throws Exception {
        List<String> body = new ArrayList<>(List.of("a", "b"));
        Exchange out = run(r -> r.split(body()).process(this::inside).end(), body);
        assertOut(SplitDefinition.class, body, out.getMessage().getBody());
        assertEquals(List.of("a", "b"), inside);
    }

    @Test
    public void aggregateKeepsTheBodyAndItsStepsGetTheAggregatedMessage() throws Exception {
        Order body = new Order();
        Exchange out = run(r -> r.aggregate(constant(true), AggregationStrategies.groupedBody()).completionSize(1)
                .process(this::inside).end(), body);
        assertOut(AggregateDefinition.class, body, out.getMessage().getBody());
        // the aggregated message goes through the steps on another thread
        await().atMost(5, TimeUnit.SECONDS).until(() -> inside.size() == 1);
        assertEquals(Carrier.LIST, carrierOf(inside.get(0)));
        assertTrue(((Collection<?>) inside.get(0)).contains(body));
    }

    @Test
    public void sortGivesAList() throws Exception {
        Exchange out = run(r -> r.sort(body()), new ArrayList<>(List.of(3, 1, 2)));
        assertOut(SortDefinition.class, null, out.getMessage().getBody());
        assertEquals(List.of(1, 2, 3), out.getMessage().getBody());
    }

    @Test
    public void claimCheckByOperation() throws Exception {
        Order body = new Order();
        Exchange out = run(r -> r.claimCheck(ClaimCheckOperation.Set, "a"), body);
        assertByOption(ClaimCheckDefinition.class, "operation", "Set", body, out.getMessage().getBody());

        out = run(r -> r.claimCheck(ClaimCheckOperation.Push).setBody(constant(END)).claimCheck(ClaimCheckOperation.Pop),
                body);
        assertSame(body, out.getMessage().getBody());
        assertEquals(Carrier.UNKNOWN, byOption(ClaimCheckDefinition.class, "operation", "Pop").carriers()[0]);
    }

    @Test
    public void convertBodyToByType() throws Exception {
        Exchange out = run(r -> r.convertBodyTo(String.class), 5);
        assertByOption(ConvertBodyDefinition.class, "type", "java.lang.String", null, out.getMessage().getBody());
        out = run(r -> r.convertBodyTo(byte[].class), "Hello");
        assertByOption(ConvertBodyDefinition.class, "type", "byte[]", null, out.getMessage().getBody());
        out = run(r -> r.convertBodyTo(List.class), new Object[] { "a" });
        assertByOption(ConvertBodyDefinition.class, "type", "java.util.List", null, out.getMessage().getBody());
    }

    // the checks of a side

    private void unchanged(Class<?> model, Consumer<ProcessorDefinition<?>> eip) throws Exception {
        Order body = new Order();
        Exchange out = run(eip, body);
        assertOut(model, body, out.getMessage().getBody());
        assertEquals(Carrier.UNCHANGED, bodyTypes(model).eipOut().value()[0], model.getSimpleName());
    }

    private void block(Class<?> model, Block eip) throws Exception {
        Order body = new Order();
        inside.clear();
        Exchange out = run(r -> eip.add(r, this::inside), body);
        assertFrom(model, BodyTypeSides.STEPS);
        assertEquals(END, out.getMessage().getBody(), model.getSimpleName());
        BodyType steps = bodyTypes(model).eipSteps();
        assertEquals(Carrier.UNCHANGED, steps.value()[0], model.getSimpleName());
        assertEquals(1, inside.size(), model.getSimpleName());
        assertSame(body, inside.get(0), model.getSimpleName());
    }

    private static void assertOut(Class<?> model, Object before, Object after) {
        BodyType out = bodyTypes(model).eipOut();
        assertTrue(out.from().isEmpty(), model.getSimpleName() + " has carriers, not from");
        assertBody(model.getSimpleName(), out.value(), out.javaType(), before, after);
    }

    private static void assertByOption(Class<?> model, String option, String value, Object before, Object after) {
        BodyType.When when = byOption(model, option, value);
        assertBody(model.getSimpleName() + " " + option + "=" + value, when.carriers(), when.javaType(), before, after);
    }

    private static void assertBody(String what, Carrier[] carriers, String javaType, Object before, Object after) {
        List<Carrier> declared = Arrays.asList(carriers);
        if (declared.contains(Carrier.UNCHANGED)) {
            assertSame(before, after, what + " leaves the body as it was");
            return;
        }
        assertTrue(declared.contains(carrierOf(after)),
                what + " says " + declared + ", but the body is " + (after != null ? after.getClass().getName() : null));
        if (!javaType.isEmpty()) {
            assertNotNull(after);
            assertTrue(javaType.equals(after.getClass().getName()) || javaType.equals(after.getClass().getCanonicalName())
                    || isA(after, javaType), what + " says " + javaType + ", but the body is " + after.getClass().getName());
        }
    }

    private static boolean isA(Object body, String javaType) {
        try {
            return Class.forName(javaType).isInstance(body);
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    private static void assertFrom(Class<?> model, String side) {
        assertEquals(side, bodyTypes(model).eipOut().from(), model.getSimpleName());
    }

    private static BodyType.When byOption(Class<?> model, String option, String value) {
        return Arrays.stream(bodyTypes(model).eipOut().byOption())
                .filter(w -> w.option().equals(option) && w.value().equals(value))
                .findFirst()
                .orElseThrow(() -> new AssertionError(model.getSimpleName() + " has no body for " + option + "=" + value));
    }

    private static BodyTypes bodyTypes(Class<?> model) {
        BodyTypes answer = model.getAnnotation(BodyTypes.class);
        assertNotNull(answer, model.getSimpleName() + " has no @BodyTypes");
        return answer;
    }

    /** The carrier of a body, as the catalog names the kinds of Java object. */
    private static Carrier carrierOf(Object body) {
        if (body == null) {
            return Carrier.NULL;
        } else if (body instanceof String) {
            return Carrier.TEXT;
        } else if (body instanceof byte[]) {
            return Carrier.BYTES;
        } else if (body instanceof InputStream || body instanceof Reader || body instanceof StreamCache) {
            return Carrier.STREAM;
        } else if (body instanceof File || body instanceof Path) {
            return Carrier.FILE;
        } else if (body instanceof Number || body instanceof Boolean || body instanceof Enum) {
            return Carrier.VALUE;
        } else if (body instanceof Map) {
            return Carrier.MAP;
        } else if (body instanceof Collection || body.getClass().isArray()) {
            return Carrier.LIST;
        } else if (body instanceof Iterator) {
            return Carrier.ITERATOR;
        }
        return Carrier.POJO;
    }

    // the routes

    private Exchange run(Consumer<ProcessorDefinition<?>> eip, Object body) throws Exception {
        String uri = "direct:eip" + routes++;
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                eip.accept(from(uri));
            }
        });
        context.start();
        return template.send(uri, e -> e.getMessage().setBody(body));
    }

    private void inside(Exchange exchange) {
        inside.add(exchange.getMessage().getBody());
    }

    @FunctionalInterface
    private interface Block {
        void add(ProcessorDefinition<?> route, Processor firstStep);
    }

    /** The sides of other models the body of an EIP comes from. */
    private static final class BodyTypeSides {
        static final String EXPRESSION_OUT = "expressionOut";
        static final String STEPS = "steps";
    }

    /** A body that no converter knows: an EIP that keeps it gives the same instance. */
    private static final class Order {
    }

    /** A policy that wraps nothing, as a transaction policy wraps its steps. */
    private static final class NoPolicy implements Policy {
        @Override
        public void beforeWrap(Route route, NamedNode definition) {
        }

        @Override
        public Processor wrap(Route route, Processor processor) {
            return processor;
        }
    }
}
