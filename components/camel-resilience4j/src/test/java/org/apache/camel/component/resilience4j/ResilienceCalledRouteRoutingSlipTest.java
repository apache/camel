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
package org.apache.camel.component.resilience4j;

import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * A route called from the steps of a circuit breaker uses the error handler of its routing slip (CAMEL-26421).
 */
class ResilienceCalledRouteRoutingSlipTest extends CamelTestSupport {
    private final AtomicInteger first = new AtomicInteger();
    private final AtomicInteger failing = new AtomicInteger();

    @Test
    void calledRouteRedeliversTheFailingStepOfItsRoutingSlip() {
        Exchange out = template.send("direct:start", e -> e.getMessage().setBody("Hello"));
        assertInstanceOf(IllegalStateException.class, out.getException());
        assertEquals(1, first.get(), "the step before the failing one is called once");
        assertEquals(3, failing.get(), "the failing step is redelivered by the error handler of the routing slip");
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").circuitBreaker().to("direct:called").end();
                from("direct:called").errorHandler(defaultErrorHandler().maximumRedeliveries(2).redeliveryDelay(0))
                        .routingSlip(constant("direct:first,direct:failing"));
                from("direct:first").errorHandler(noErrorHandler()).process(e -> first.incrementAndGet());
                from("direct:failing").errorHandler(noErrorHandler()).process(e -> {
                    failing.incrementAndGet();
                    throw new IllegalStateException("Failing step");
                });
            }
        };
    }
}
