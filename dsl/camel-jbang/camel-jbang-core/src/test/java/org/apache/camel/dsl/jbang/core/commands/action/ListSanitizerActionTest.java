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
package org.apache.camel.dsl.jbang.core.commands.action;

import java.util.List;
import java.util.Map;

import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.commands.process.ListSanitizer;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * camel get sanitizer against an integration that answers its action, as the CLI connector does: the sanitizer action,
 * the answer as JSON text, and an answer without the console.
 */
@ExtendWith(MockitoExtension.class)
class ListSanitizerActionTest extends ActionCommandTestSupport {

    @Test
    void asksForTheSanitizerAndListsTheViolations() throws Exception {
        writeStatusFile(TEST_PID, "orders");

        assertThat(callWithResponse(new ListSanitizer(new CamelJBangMain().withPrinter(printer)), sanitizer()))
                .isZero();

        assertThat(readActionFile(TEST_PID).getString("action")).isEqualTo("sanitizer");
        // the table does not show the reports
        assertThat(readActionFile(TEST_PID).getString("report")).isEqualTo("false");
        String line = printer.getOutput().lines().filter(l -> l.contains("EX-1")).findFirst().orElseThrow();
        assertThat(line).contains("orders", "R1", "process threw before calling the callback", "price");
    }

    @Test
    void anAnswerWithoutTheConsoleIsAnIntegrationWithoutCamelSanitizer() throws Exception {
        writeStatusFile(TEST_PID, "orders");

        assertThat(callWithResponse(new ListSanitizer(new CamelJBangMain().withPrinter(printer)), new JsonObject()))
                .isZero();

        assertThat(printer.getOutput())
                .contains("No running integration has camel-sanitizer: run it with camel run --sanitizer");
    }

    private static JsonObject sanitizer() {
        JsonObject violation = new JsonObject();
        violation.put("rule", "R1");
        violation.put("kind", "THREW_BEFORE_CALLBACK");
        violation.put("title", "process threw before calling the callback");
        violation.put("exchangeId", "EX-1");
        violation.put("routeId", "orders");
        violation.put("stepId", "price");
        violation.put("report", "Exchange sanitizer: process threw before calling the callback (R1)");
        JsonObject sanitizer = new JsonObject();
        sanitizer.put("enabled", true);
        sanitizer.put("calls", 3);
        sanitizer.put("total", 1);
        sanitizer.put("rules", new JsonObject(Map.of("R1", 1)));
        sanitizer.put("violations", new JsonArray(List.of(violation)));
        return sanitizer;
    }
}
