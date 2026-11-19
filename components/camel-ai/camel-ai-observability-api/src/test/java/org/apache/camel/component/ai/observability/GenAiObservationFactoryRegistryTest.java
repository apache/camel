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
package org.apache.camel.component.ai.observability;

import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.Exchange;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.test.junit6.ExchangeTestSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GenAiObservationFactoryRegistryTest extends ExchangeTestSupport {

    private final AtomicInteger started = new AtomicInteger();
    private final GenAiObservation observation = new GenAiObservation() {
        @Override
        public void recordSuccess(GenAiUsage usage) {
        }

        @Override
        public void recordError(Throwable error) {
        }

        @Override
        public void close() {
        }
    };

    @Test
    void shouldUseFactoryFromRegistry() {
        context.getRegistry().bind("genAiObservationFactory", (GenAiObservationFactory) (exchange, ctx) -> {
            started.incrementAndGet();
            return observation;
        });

        assertThat(GenAiObservability.start(newExchange(), observationContext())).isSameAs(observation);
        assertThat(started).hasValue(1);
    }

    @Test
    void shouldReturnNoopWhenFactoryFails() {
        context.getRegistry().bind("genAiObservationFactory", (GenAiObservationFactory) (exchange, ctx) -> {
            throw new IllegalStateException("boom");
        });

        GenAiObservation result = GenAiObservability.start(newExchange(), observationContext());

        assertThat(result).isNotNull().isNotSameAs(observation);
        result.close();
    }

    @Test
    void shouldReturnNoopWhenFactoryFailsWithLinkageError() {
        // as a handler built against another Micrometer version would
        context.getRegistry().bind("genAiObservationFactory", (GenAiObservationFactory) (exchange, ctx) -> {
            throw new NoSuchMethodError("'void io.micrometer.observation.Observation$Context.example()'");
        });

        GenAiObservation result = GenAiObservability.start(newExchange(), observationContext());

        assertThat(result).isNotNull().isNotSameAs(observation);
        result.close();
    }

    private Exchange newExchange() {
        return new DefaultExchange(context);
    }

    private static GenAiObservationContext observationContext() {
        return GenAiObservationContext.builder()
                .operationName(GenAiOperationName.CHAT)
                .requestModel("gpt-4o")
                .build();
    }
}
