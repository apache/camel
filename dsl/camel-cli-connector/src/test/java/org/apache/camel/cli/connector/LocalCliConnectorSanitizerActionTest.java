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

import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.apache.camel.AsyncCallback;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.spi.CliConnectorFactory;
import org.apache.camel.support.AsyncProcessorSupport;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The sanitizer action of the CLI connector, as camel get sanitizer sends it: the answer is the sanitizer dev console,
 * with the violations of the integration, or why the sanitizer is off.
 */
class LocalCliConnectorSanitizerActionTest {

    private CamelContext context;
    private LocalCliConnector connector;

    @AfterEach
    void stop() {
        if (connector != null) {
            connector.stop();
        }
        if (context != null) {
            context.stop();
        }
    }

    private void start(String profile) throws Exception {
        context = new DefaultCamelContext();
        // the action is dispatched here: a connector of the context would poll the {pid} files of other tests
        DefaultCliConnectorFactory disabled = new DefaultCliConnectorFactory();
        disabled.setEnabled(false);
        context.getCamelContextExtension().addContextPlugin(CliConnectorFactory.class, disabled);
        context.getCamelContextExtension().setProfile(profile);
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").routeId("orders").process(new ThrowsInProcess()).id("price");
            }
        });
        context.start();
        connector = new LocalCliConnector(new DefaultCliConnectorFactory());
        connector.setCamelContext(context);
    }

    private JsonObject sanitizer() throws Exception {
        return sanitizer(new JsonObject());
    }

    private JsonObject sanitizer(JsonObject action) throws Exception {
        action.put("action", "sanitizer");
        CliActionOutcome outcome = CliActionOutcome.dispatch(connector, (JsonObject) Jsoner.deserialize(action.toJson()))
                .get(10, TimeUnit.SECONDS);
        assertThat(outcome.error()).isNull();
        return outcome.result();
    }

    @Test
    void theViolationsOfTheIntegration() throws Exception {
        start("dev");
        context.createProducerTemplate().send("direct:start", e -> e.getMessage().setBody("x"));

        JsonObject answer = sanitizer();

        assertThat(answer.getBoolean("enabled")).isTrue();
        assertThat(answer.getLong("total")).isEqualTo(1);
        JsonArray violations = (JsonArray) answer.get("violations");
        assertThat(violations).hasSize(1);
        JsonObject violation = violations.getMap(0);
        assertThat(violation.getString("kind")).isEqualTo("THREW_BEFORE_CALLBACK");
        assertThat(violation.getString("routeId")).isEqualTo("orders");
        assertThat(violation.getString("stepId")).isEqualTo("price");
        assertThat(violation.getString("processor")).isEqualTo(ThrowsInProcess.class.getName());
        assertThat(violation.getString("report")).contains("Forced by ThrowsInProcess");
    }

    @Test
    void theReportsAreLeftOutWithReportFalse() throws Exception {
        start("dev");
        context.createProducerTemplate().send("direct:start", e -> e.getMessage().setBody("x"));

        // as camel get sanitizer asks for its table
        JsonObject answer = sanitizer(new JsonObject(Map.of("report", "false")));

        assertThat(answer.getLong("total")).isEqualTo(1);
        JsonObject violation = ((JsonArray) answer.get("violations")).getMap(0);
        assertThat(violation.getString("kind")).isEqualTo("THREW_BEFORE_CALLBACK");
        assertThat(violation.getString("stepId")).isEqualTo("price");
        assertThat(violation).doesNotContainKey("report");
    }

    @Test
    void offWithProfileProd() throws Exception {
        start("prod");

        JsonObject answer = sanitizer();

        assertThat(answer.getBoolean("enabled")).isFalse();
        assertThat(answer.getString("reason")).isEqualTo("The sanitizer is off: it stays off with profile prod");
        assertThat(answer).doesNotContainKey("violations");
    }

    /** Throws from process instead of setting the exception and calling the callback. */
    private static final class ThrowsInProcess extends AsyncProcessorSupport {

        @Override
        public boolean process(Exchange exchange, AsyncCallback callback) {
            throw new IllegalStateException("Forced by ThrowsInProcess");
        }
    }
}
