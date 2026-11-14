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
package org.apache.camel.dsl.jbang.core.commands.ai;

import java.util.List;

import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CAMEL-26932: the AI tools give the lines of source code as they are in the file. The dev consoles escape each line of
 * a code list, and the agent saw \" that is not in the file.
 */
class ToolContextCodeLinesTest {

    private static final String LINE = "        - setBody: { simple: \"Hello ${header.name}\" }  # C:\\temp";

    @Test
    void theLinesOfARouteDumpAreAsInTheFile() throws Exception {
        // route-dump, source and route-structure: routes, each with a list of lines
        JsonObject answer = new JsonObject();
        answer.put("routes", new JsonArray(List.of(route(LINE))));

        assertThat(ToolContext.unescapeCodeLines(answer)).isTrue();

        JsonObject route = (JsonObject) ((JsonArray) answer.get("routes")).get(0);
        List<JsonObject> code = route.getCollection("code");
        assertThat(code.get(0).getString("code")).isEqualTo(LINE);
        assertThat(code.get(0).getInteger("line")).isEqualTo(7);
        // what the agent reads is the file, also after a JSON round trip
        JsonObject parsed = (JsonObject) Jsoner.deserialize(answer.toJson());
        JsonObject again = (JsonObject) ((JsonArray) parsed.get("routes")).get(0);
        assertThat(((JsonObject) ((JsonArray) again.get("code")).get(0)).getString("code")).isEqualTo(LINE);
    }

    @Test
    void aLineWithoutEscapesIsNoChange() {
        JsonObject answer = new JsonObject();
        answer.put("routes", new JsonArray(List.of(route("- from: timer:tick"))));

        assertThat(ToolContext.unescapeCodeLines(answer)).isFalse();
    }

    @Test
    void otherTextIsAsItWas() {
        // a message body or header is not a line of code, and is left as the integration wrote it
        JsonObject message = new JsonObject();
        message.put("body", Jsoner.escape("{\"code\":\"x\"}"));
        message.put("code", "A\\\"B");
        JsonObject answer = new JsonObject();
        answer.put("message", message);

        assertThat(ToolContext.unescapeCodeLines(answer)).isFalse();
        assertThat(message.getString("code")).isEqualTo("A\\\"B");
    }

    private static JsonObject route(String text) {
        JsonObject line = new JsonObject();
        line.put("line", 7);
        // as the dev consoles write a line
        line.put("code", Jsoner.escape(text));
        JsonObject route = new JsonObject();
        route.put("routeId", "orders");
        route.put("code", new JsonArray(List.of(line)));
        return route;
    }
}
