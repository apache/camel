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
package org.apache.camel.component.microprofile.faulttolerance;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.apache.camel.Exchange;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.CircuitBreakerDefinition;
import org.apache.camel.model.OnFallbackDefinition;
import org.apache.camel.spi.BodyType.Carrier;
import org.apache.camel.spi.BodyTypes;
import org.apache.camel.spi.CircuitBreakerConstants;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.eclipse.microprofile.faulttolerance.exceptions.CircuitBreakerOpenException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The body types of the circuitBreaker and onFallback EIPs (CAMEL-25928), checked at runtime with MicroProfile Fault
 * Tolerance: the steps start with the body the circuit breaker got, and the body after it is what its steps end with,
 * or what onFallback ends with when a step failed or the circuit is open. The EIPs implemented in core are checked by
 * EipBodyTypesTest in camel-core.
 */
public class FaultToleranceBodyTypesTest extends CamelTestSupport {

    /** The body the steps of circuitBreaker end with. */
    private static final String END = "end of the steps";
    /** The body the steps of onFallback end with. */
    private static final String FALLBACK_END = "end of onFallback";
    /** The body a step sets before the next step fails. */
    private static final String FAILED = "the body the failed step left";

    private final List<Object> steps = new CopyOnWriteArrayList<>();
    private final List<Object> fallback = new CopyOnWriteArrayList<>();

    @Test
    public void theDeclarations() {
        assertEquals("steps", bodyTypes(CircuitBreakerDefinition.class).eipOut().from());
        assertEquals(Carrier.UNCHANGED, bodyTypes(CircuitBreakerDefinition.class).eipSteps().value()[0]);
        assertEquals("steps", bodyTypes(OnFallbackDefinition.class).eipOut().from());
        assertEquals(Carrier.UNCHANGED, bodyTypes(OnFallbackDefinition.class).eipSteps().value()[0]);
    }

    @Test
    public void theStepsStartWithTheBodyAndTheBodyAfterIsWhatTheyEndWith() {
        Order body = new Order();
        Exchange out = send("direct:steps", body);
        assertFalse(out.isFailed());
        assertEquals(END, out.getMessage().getBody());
        assertEquals(1, steps.size());
        assertSame(body, steps.get(0));
        assertTrue(fallback.isEmpty());
    }

    @Test
    public void whenAStepFailsOnFallbackStartsWithTheBodyTheCircuitBreakerGot() {
        Order body = new Order();
        Exchange out = send("direct:failed", body);
        assertFalse(out.isFailed());
        assertEquals(FALLBACK_END, out.getMessage().getBody());
        assertEquals(1, steps.size());
        assertSame(body, steps.get(0));
        // the steps run on a copy of the exchange, so onFallback gets the body the circuit breaker got,
        // not the body the failed step left
        assertEquals(1, fallback.size());
        assertSame(body, fallback.get(0));
        assertEquals(false, out.getProperty(CircuitBreakerConstants.RESPONSE_REJECTED));
    }

    @Test
    public void whenTheCircuitIsOpenOnFallbackStartsWithTheBodyTheCircuitBreakerGot() {
        // two failures open the circuit, the third call is not attempted
        send("direct:open", new Order());
        send("direct:open", new Order());

        Order body = new Order();
        Exchange out = send("direct:open", body);
        assertFalse(out.isFailed());
        assertEquals(FALLBACK_END, out.getMessage().getBody());
        assertTrue(steps.isEmpty());
        assertEquals(1, fallback.size());
        assertSame(body, fallback.get(0));
        assertEquals(true, out.getProperty(CircuitBreakerConstants.RESPONSE_REJECTED));
        assertInstanceOf(CircuitBreakerOpenException.class, out.getProperty(Exchange.EXCEPTION_CAUGHT));
    }

    private Exchange send(String uri, Object body) {
        steps.clear();
        fallback.clear();
        return template.send(uri, e -> e.getMessage().setBody(body));
    }

    private void steps(Exchange exchange) {
        steps.add(exchange.getMessage().getBody());
    }

    private void fallback(Exchange exchange) {
        fallback.add(exchange.getMessage().getBody());
    }

    private static BodyTypes bodyTypes(Class<?> model) {
        BodyTypes answer = model.getAnnotation(BodyTypes.class);
        assertNotNull(answer, model.getSimpleName() + " has no @BodyTypes");
        return answer;
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:steps")
                        .circuitBreaker()
                        .process(e -> steps(e)).setBody(constant(END))
                        .onFallback()
                        .process(e -> fallback(e)).setBody(constant(FALLBACK_END))
                        .end();

                from("direct:failed")
                        .circuitBreaker()
                        .process(e -> steps(e)).setBody(constant(FAILED))
                        .throwException(new IllegalStateException("Forced"))
                        .onFallback()
                        .process(e -> fallback(e)).setBody(constant(FALLBACK_END))
                        .end();

                from("direct:open")
                        .circuitBreaker()
                        .faultToleranceConfiguration().requestVolumeThreshold(2).failureRatio(50).delay(60000).end()
                        .process(e -> steps(e)).setBody(constant(FAILED))
                        .throwException(new IllegalStateException("Forced"))
                        .onFallback()
                        .process(e -> fallback(e)).setBody(constant(FALLBACK_END))
                        .end();
            }
        };
    }

    /** A body that no converter knows: a circuit breaker that keeps it gives the same instance. */
    private static final class Order {
    }
}
