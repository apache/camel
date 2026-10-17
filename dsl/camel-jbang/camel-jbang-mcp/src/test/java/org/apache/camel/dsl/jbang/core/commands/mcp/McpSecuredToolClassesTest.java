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

import java.util.List;

import io.quarkiverse.mcp.server.Tool;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every class of the MCP module that exposes a {@link Tool} must carry {@link McpSecured}: the security interceptor
 * (audit, argument sanitisation, secret redaction) is bound by that annotation only, so a tool class without it
 * silently skips all of it.
 */
class McpSecuredToolClassesTest {

    @Test
    void everyToolClassIsMcpSecured() throws Exception {
        List<Class<?>> toolClasses = McpToolClasses.toolClasses();

        // guard against a scan that finds nothing and makes the check below vacuous
        assertThat(toolClasses).hasSizeGreaterThan(20).contains(AuthoringTools.class, CatalogTools.class);
        assertThat(toolClasses)
                .filteredOn(c -> !c.isAnnotationPresent(McpSecured.class))
                .as("tool classes without @McpSecured")
                .isEmpty();
    }
}
