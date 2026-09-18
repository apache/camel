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

import org.apache.camel.Exchange;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.test.junit6.ExchangeTestSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GenAiObservabilityMissingImplLogTest extends ExchangeTestSupport {

    @Test
    void shouldLogInfoOnceWhenImplementationIsMissing() {
        try (LogCapture capture = LogCapture.attach(GenAiObservability.class)) {
            Exchange exchange = new DefaultExchange(context);
            GenAiObservationContext observationContext = GenAiObservationContext.builder()
                    .operationName(GenAiOperationName.CHAT)
                    .requestModel("gpt-4o")
                    .build();
            GenAiObservability.start(exchange, observationContext).close();
            GenAiObservability.start(exchange, observationContext).close();

            assertThat(capture.infoMessages()).hasSize(1);
            assertThat(capture.infoMessages().get(0))
                    .contains("camel-ai-observability is not on the classpath");
        }
    }
}
