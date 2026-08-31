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

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.SagaPropagation;
import org.apache.camel.saga.CamelSagaCoordinator;
import org.apache.camel.saga.CamelSagaStep;
import org.apache.camel.saga.InMemorySagaService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The saga id normally travels in the exchange's internal state. It is also readable from the
 * {@code Long-Running-Action} header so a coordinator started elsewhere can be joined, which is how the LRA protocol
 * carries it - but that header sits outside the {@code Camel} namespace consumers filter, and the id is written back
 * onto responses, so under a service with no external coordinator it would let a message choose which saga its exchange
 * joins.
 * <p>
 * Asserted by watching which ids reach {@code getSaga}, rather than by joining a live saga: a saga started by another
 * route has already completed by the time a second exchange could present its id, so that would fail for the wrong
 * reason.
 */
public class SagaHeaderCannotSelectCoordinatorTest extends ContextTestSupport {

    private final RecordingSagaService sagaService = new RecordingSagaService();

    @Test
    public void aMessageSuppliedIdIsNotLookedUp() {
        Exchange exchange = template.request("direct:mandatory", e -> {
            e.getIn().setHeader(Exchange.SAGA_LONG_RUNNING_ACTION, "a-saga-id-from-the-wire");
            e.getIn().setBody("hello");
        });

        assertTrue(exchange.isFailed(), "MANDATORY has no saga to join, so the exchange must fail");
        assertEquals(List.of(), sagaService.lookedUp,
                "the coordinator must not be looked up from an id supplied by the message");
    }

    @Test
    public void theInMemoryServiceDoesNotAdvertiseHeaderSupport() {
        assertFalse(new InMemorySagaService().isLongRunningActionHeaderSupported());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                context.addService(sagaService);

                from("direct:mandatory").saga().propagation(SagaPropagation.MANDATORY)
                        .transform().constant("joined");
            }
        };
    }

    /**
     * Delegates to the in-memory service, recording every id it is asked to resolve.
     */
    private static final class RecordingSagaService extends InMemorySagaService {

        private final List<String> lookedUp = new CopyOnWriteArrayList<>();

        @Override
        public CompletableFuture<CamelSagaCoordinator> getSaga(String id) {
            lookedUp.add(id);
            return super.getSaga(id);
        }

        @Override
        public void registerStep(CamelSagaStep step) {
            super.registerStep(step);
        }
    }
}
