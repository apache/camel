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

import io.micrometer.observation.ObservationRegistry;
import io.micrometer.tracing.handler.DefaultTracingObservationHandler;
import io.micrometer.tracing.test.simple.SimpleTracer;
import org.apache.camel.Exchange;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.test.junit6.ExchangeTestSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GenAiObservabilityWithTracingNoLogTest extends ExchangeTestSupport {

    @Test
    void shouldNotLogWhenObservationRegistryHasTracingHandler() {
        ObservationRegistry observationRegistry = ObservationRegistry.create();
        observationRegistry.observationConfig()
                .observationHandler(new DefaultTracingObservationHandler(new SimpleTracer()));
        context.getRegistry().bind("observationRegistry", observationRegistry);

        try (LogCapture capture = LogCapture.attach(GenAiMicrometerObservationSupport.class)) {
            observeTwice();
            assertThat(capture.infoMessages()).isEmpty();
        }
    }

    private void observeTwice() {
        GenAiObservationContext observationContext = GenAiObservationContext.builder()
                .operationName(GenAiOperationName.CHAT)
                .system("openai")
                .requestModel("test-model")
                .componentScheme("langchain4j-chat")
                .build();
        Exchange exchange = new DefaultExchange(context);
        GenAiObservability.start(exchange, observationContext).close();
        GenAiObservability.start(exchange, observationContext).close();
    }
}
