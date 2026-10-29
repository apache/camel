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

import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.spi.CliConnectorFactory;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CAMEL-26400: the eval action passes the resultType of the call to the eval-language console, and a header or variable
 * keeps the JSON type of its value (a list of rows), as camel_eval_expression sends them.
 */
class LocalCliConnectorEvalActionTest {

    @Test
    void theResultTypeGoesToTheConsole() throws Exception {
        JsonObject request = new JsonObject();
        request.put("action", "eval");
        request.put("language", "simple");
        request.put("template", "${body}");
        request.put("body", "0042");
        request.put("resultType", "java.lang.Integer");

        JsonObject answer = dispatch(request);

        assertThat(answer.getString("status")).as(answer.toJson()).isEqualTo("success");
        assertThat(answer.getString("result")).isEqualTo("42");
    }

    @Test
    void aHeaderAndAVariableKeepTheTypeOfTheirValue() throws Exception {
        JsonArray rows = new JsonArray();
        JsonObject row = new JsonObject();
        row.put("total", 3);
        rows.add(row);
        JsonObject request = new JsonObject();
        request.put("action", "eval");
        request.put("language", "simple");
        request.put("template", "${header.rows.size()} ${variable.count}");
        request.put("headers", new JsonArray(List.of(keyValue("rows", rows))));
        request.put("variables", new JsonArray(List.of(keyValue("count", 7))));

        // as the action file carries it: JSON text
        JsonObject answer = dispatch((JsonObject) Jsoner.deserialize(request.toJson()));

        assertThat(answer.getString("status")).as(answer.toJson()).isEqualTo("success");
        assertThat(answer.getString("result")).isEqualTo("1 7");
    }

    private static JsonObject dispatch(JsonObject request) throws Exception {
        try (var context = new DefaultCamelContext()) {
            // the action is dispatched here: a connector of the context would poll the {pid} files of other tests
            var disabled = new DefaultCliConnectorFactory();
            disabled.setEnabled(false);
            context.getCamelContextExtension().addContextPlugin(CliConnectorFactory.class, disabled);
            context.start();
            var connector = new LocalCliConnector(new DefaultCliConnectorFactory());
            connector.setCamelContext(context);
            var answer = new AtomicReference<JsonObject>();
            assertThat(connector.dispatch(request, answer::set)).isTrue();
            return answer.get();
        }
    }

    private static JsonObject keyValue(String key, Object value) {
        JsonObject jo = new JsonObject();
        jo.put("key", key);
        jo.put("value", value);
        return jo;
    }
}
