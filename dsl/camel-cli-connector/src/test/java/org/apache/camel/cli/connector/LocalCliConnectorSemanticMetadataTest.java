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
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.camel.console.DevConsoleRegistry;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.spi.CliConnectorFactory;
import org.apache.camel.support.console.AbstractDevConsole;
import org.apache.camel.util.json.JsonObject;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LocalCliConnectorSemanticMetadataTest {
    @Test
    void advertisesSemanticMetadataBeforeItsFirstUseWithoutCallingIt() throws Exception {
        try (var context = new DefaultCamelContext()) {
            // This test only inspects metadata; an auto-started connector would share PID files with other tests.
            var disabled = new DefaultCliConnectorFactory();
            disabled.setEnabled(false);
            context.getCamelContextExtension().addContextPlugin(CliConnectorFactory.class, disabled);
            context.start();
            var connector = new LocalCliConnector(new DefaultCliConnectorFactory());
            connector.setCamelContext(context);
            assertThat(connector.status().<List<String>> getCollection("devConsoles"))
                    .doesNotContain("semantic-metadata");
            context.getRegistry().bind("semantic-metadata-dev-console",
                    new AbstractDevConsole("camel", "semantic-metadata", "Test", "Test") {
                        @Override
                        protected String doCallText(Map<String, Object> options) {
                            throw new AssertionError("Discovery must not call the console");
                        }

                        @Override
                        protected Map<String, Object> doCallJson(Map<String, Object> options) {
                            throw new AssertionError("Discovery must not call the console");
                        }
                    });
            assertThat(DevConsoleRegistry.get(context).getConsoleIDs()).doesNotContain("semantic-metadata");
            assertThat(connector.status().<List<String>> getCollection("devConsoles"))
                    .contains("semantic-metadata");
        }
    }

    @Test
    void passesExpertSelectionToTheOptionalMetadataConsole() throws Exception {
        try (var context = new DefaultCamelContext()) {
            var connector = new LocalCliConnector(new DefaultCliConnectorFactory());
            connector.setCamelContext(context);
            var answer = new AtomicReference<JsonObject>();
            JsonObject request = new JsonObject();
            request.put("action", "semantic-metadata");
            request.put("expert", "{{selected.expert}}");
            assertThat(connector.dispatch(request, answer::set)).isTrue();
            assertThat(answer.get()).isEmpty();

            DevConsoleRegistry.get(context).register(new AbstractDevConsole("camel", "semantic-metadata", "Test", "Test") {
                @Override
                protected String doCallText(Map<String, Object> options) {
                    return "";
                }

                @Override
                protected Map<String, Object> doCallJson(Map<String, Object> options) {
                    JsonObject response = new JsonObject();
                    response.putAll(options);
                    return response;
                }
            });
            assertThat(connector.dispatch(request, answer::set)).isTrue();
            assertThat(answer.get()).containsOnlyKeys("expert").containsEntry("expert", "{{selected.expert}}");
            request.put("overview", true);
            request.put("ignored", "not a console option");
            assertThat(connector.dispatch(request, answer::set)).isTrue();
            assertThat(answer.get()).containsOnlyKeys("expert", "overview").containsEntry("overview", true);
            request.remove("overview");
            request.remove("expert");
            assertThat(connector.dispatch(request, answer::set)).isTrue();
            assertThat(answer.get()).isEmpty();
        }
    }

    @Test
    void forwardsTypedSampleExchangeToEvaluationConsole() throws Exception {
        try (var context = new DefaultCamelContext()) {
            var connector = new LocalCliConnector(new DefaultCliConnectorFactory());
            connector.setCamelContext(context);
            var answer = new AtomicReference<JsonObject>();
            JsonObject request = new JsonObject();
            request.put("action", "semantic-evaluate");
            request.put("evaluation", "classify");
            request.put("body", Map.of("items", List.of(1, 2), "active", true));
            request.put("headers", Map.of("size", 2));
            request.put("variables", Map.of("labels", List.of("a", "b")));
            request.put("expert", "security");
            request.put("operation", "injection");
            request.put("input", "literal ${body} text");
            request.put("parameters", Map.of("threshold", 0.5));
            request.put("ignored", "not a console option");
            assertThat(connector.dispatch(request, answer::set)).isTrue();
            assertThat(answer.get()).isEmpty();
            DevConsoleRegistry.get(context).register(new AbstractDevConsole("camel", "semantic-evaluate", "Test", "Test") {
                @Override
                protected String doCallText(Map<String, Object> options) {
                    return "";
                }

                @Override
                protected Map<String, Object> doCallJson(Map<String, Object> options) {
                    JsonObject response = new JsonObject();
                    response.putAll(options);
                    return response;
                }
            });
            assertThat(connector.dispatch(request, answer::set)).isTrue();
            assertThat(answer.get())
                    .containsOnlyKeys("evaluation", "body", "headers", "variables", "expert", "operation", "input",
                            "parameters")
                    .containsEntry("evaluation", "classify").containsEntry("body", request.get("body"))
                    .containsEntry("headers", request.get("headers")).containsEntry("variables", request.get("variables"))
                    .containsEntry("expert", "security").containsEntry("operation", "injection")
                    .containsEntry("input", "literal ${body} text").containsEntry("parameters", Map.of("threshold", 0.5));
        }
    }
}
