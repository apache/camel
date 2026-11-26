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
package org.apache.camel.cli.connector;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.camel.component.ai.observability.GenAiObservability;
import org.apache.camel.component.ai.observability.GenAiObservation;
import org.apache.camel.component.ai.observability.GenAiObservationContext;
import org.apache.camel.component.ai.observability.GenAiUsage;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.spi.CliConnectorFactory;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.util.json.JsonObject;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CAMEL-27313: the genai action answers with the genai console of camel-ai-observability, which the Camel CLI reads for
 * the GenAI calls of an integration.
 */
class LocalCliConnectorGenAiActionTest {

    @Test
    void theGenAiActionAnswersWithTheCallsOfTheConsole() throws Exception {
        try (var context = new DefaultCamelContext()) {
            context.setDevConsole(true);
            // the action is dispatched here: a connector of the context would poll the {pid} files of other tests
            var disabled = new DefaultCliConnectorFactory();
            disabled.setEnabled(false);
            context.getCamelContextExtension().addContextPlugin(CliConnectorFactory.class, disabled);
            context.start();
            GenAiObservation chat = GenAiObservability.start(new DefaultExchange(context),
                    GenAiObservationContext.builder().system("ollama").requestModel("granite4:3b").build());
            chat.recordSuccess(GenAiUsage.of(12L, 3L, "stop", null));
            chat.close();
            var connector = new LocalCliConnector(new DefaultCliConnectorFactory());
            connector.setCamelContext(context);
            JsonObject request = new JsonObject();
            request.put("action", "genai");
            request.put("limit", "5");

            var answer = new AtomicReference<JsonObject>();
            assertThat(connector.dispatch(request, answer::set)).isTrue();

            assertThat(answer.get()).containsEntry("recording", true).containsEntry("modelCalls", 1L)
                    .containsEntry("inputTokens", 12L);
            List<JsonObject> calls = answer.get().getCollection("calls");
            assertThat(calls).singleElement().satisfies(call -> assertThat(call).containsEntry("name", "chat granite4:3b"));
        }
    }
}
