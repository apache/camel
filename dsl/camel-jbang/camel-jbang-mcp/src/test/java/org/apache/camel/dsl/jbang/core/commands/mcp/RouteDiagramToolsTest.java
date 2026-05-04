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
package org.apache.camel.dsl.jbang.core.commands.mcp;

import io.quarkiverse.mcp.server.ToolCallException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RouteDiagramToolsTest {

    @Test
    void missingSourceFileThrows() {
        RouteDiagramTools tools = new RouteDiagramTools();

        assertThatThrownBy(() -> tools.camel_render_route_diagram(null, null, null, null, null, null))
                .isInstanceOf(ToolCallException.class)
                .hasMessageContaining("sourceFile");
    }

    @Test
    void blankSourceFileThrows() {
        RouteDiagramTools tools = new RouteDiagramTools();

        assertThatThrownBy(() -> tools.camel_render_route_diagram("   ", null, null, null, null, null))
                .isInstanceOf(ToolCallException.class)
                .hasMessageContaining("sourceFile");
    }

    @Test
    void nonExistingSourceFileThrows() {
        RouteDiagramTools tools = new RouteDiagramTools();

        assertThatThrownBy(() -> tools.camel_render_route_diagram(
                "/no/such/file.yaml", null, null, null, null, null))
                .isInstanceOf(ToolCallException.class)
                .hasMessageContaining("does not exist");
    }
}
