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
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import picocli.CommandLine;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * camel get source: the source of the routes, as the integration answers it.
 */
@ExtendWith(MockitoExtension.class)
class CamelSourceActionRoutesTest extends ActionCommandTestSupport {

    @Test
    void routesWithoutSourceLocation() throws Exception {
        writeStatusFile(TEST_PID, "orders");
        // such as routes a RouteBuilder adds without a source location
        JsonObject answer = new JsonObject(
                Map.of("routes", new JsonArray(List.of(route("file:/work/orders.yaml"), route(null), route(null)))));

        assertThat(callWithResponse(command("orders", "--json"), answer)).isZero();

        JsonArray rows = Jsoner.deserialize(printer.getStdout(), (JsonArray) null);
        // the routes without a location are listed once, and first
        assertThat(rows).hasSize(2);
        JsonObject first = rows.getMap(0);
        assertThat(first.getString("location")).isNull();
        JsonObject second = rows.getMap(1);
        assertThat(second.getString("location")).isEqualTo("orders.yaml");
    }

    @Test
    void answerWithoutRoutes() throws Exception {
        writeStatusFile(TEST_PID, "orders");

        assertThat(callWithResponse(command("orders", "--json"), new JsonObject())).isZero();

        assertThat(printer.getStdout()).isEqualTo("[]");
    }

    private CamelSourceAction command(String... args) {
        CamelSourceAction command = new CamelSourceAction(new CamelJBangMain().withPrinter(printer));
        CommandLine.populateCommand(command, args);
        return command;
    }

    private static JsonObject route(String source) {
        JsonObject line = new JsonObject();
        line.put("line", 1);
        line.put("code", "- route:");
        JsonObject route = new JsonObject();
        if (source != null) {
            route.put("source", source);
        }
        route.put("code", new JsonArray(List.of(line)));
        return route;
    }
}
