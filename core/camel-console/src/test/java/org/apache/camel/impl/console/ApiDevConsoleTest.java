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
package org.apache.camel.impl.console;

import org.apache.camel.console.DevConsole;
import org.apache.camel.console.DevConsoleRegistry;
import org.apache.camel.util.json.JsonObject;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ApiDevConsoleTest extends AbstractDevConsoleTest {

    private JsonObject openApiPaths() {
        DevConsoleRegistry dcr = context.getCamelContextExtension().getContextPlugin(DevConsoleRegistry.class);
        dcr.loadDevConsoles();

        DevConsole con = assertConsoleExists("api", "camel");
        JsonObject root = callJson(con);
        JsonObject paths = root.getJsonObject("paths");
        assertThat(paths).isNotNull();
        return paths;
    }

    @Test
    public void testReadOnlyConsoleUsesGet() {
        JsonObject paths = openApiPaths();

        JsonObject contextPath = paths.getJsonObject("/q/dev/context");
        assertThat(contextPath).as("context console path should exist").isNotNull();
        assertThat(contextPath.getJsonObject("get")).as("context console should be declared as GET").isNotNull();
        assertThat(contextPath.getJsonObject("post")).as("context console should not be declared as POST").isNull();
    }

    @Test
    public void testMutatingConsoleUsesPost() {
        JsonObject paths = openApiPaths();

        JsonObject routePath = paths.getJsonObject("/q/dev/route");
        assertThat(routePath).as("route console path should exist").isNotNull();
        assertThat(routePath.getJsonObject("get")).as("route console should not be declared as GET").isNull();

        JsonObject post = routePath.getJsonObject("post");
        assertThat(post).as("route console should be declared as POST").isNotNull();
        assertThat(post.get("requestBody")).as("route console POST should carry a requestBody").isNotNull();
    }

    @Test
    public void testIsReadOnlyDefaults() {
        assertThat(new ContextDevConsole().isReadOnly()).isTrue();
        assertThat(new RouteDevConsole().isReadOnly()).isFalse();
        assertThat(new EvalLanguageDevConsole().isReadOnly()).isFalse();
        assertThat(new SendDevConsole().isReadOnly()).isFalse();
    }
}
