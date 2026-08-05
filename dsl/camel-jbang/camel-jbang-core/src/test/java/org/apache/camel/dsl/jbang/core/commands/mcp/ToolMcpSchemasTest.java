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

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ToolMcpSchemasTest {

    @Test
    void buildsRequiredParametersSchema() {
        String schema = ToolMcpSchemas.inputSchemaJson(
                org.apache.camel.dsl.jbang.core.commands.ai.ToolDescriptor.tool("demo", "Demo tool")
                        .param("name", "string", "A name", true)
                        .param("count", "integer", "Optional count", false));

        assertThat(schema).contains("\"name\"");
        assertThat(schema).contains("\"required\"");
        assertThat(schema).contains("name");
    }
}
