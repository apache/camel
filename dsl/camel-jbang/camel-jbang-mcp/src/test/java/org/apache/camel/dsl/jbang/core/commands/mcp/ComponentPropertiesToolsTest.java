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

import java.util.Optional;

import io.quarkiverse.mcp.server.ToolCallException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ComponentPropertiesToolsTest {

    private ComponentPropertiesTools createTools() {
        CatalogService catalogService = new CatalogService();
        catalogService.catalogRepos = Optional.empty();

        ComponentPropertiesTools tools = new ComponentPropertiesTools();
        tools.catalogService = catalogService;
        return tools;
    }

    @Test
    void listsTimerProperties() {
        ComponentPropertiesTools tools = createTools();

        ComponentPropertiesTools.ComponentPropertiesResult result
                = tools.camel_component_properties("timer", null, null, null);

        assertThat(result).isNotNull();
        assertThat(result.scheme()).isEqualTo("timer");
        assertThat(result.prefix()).isEqualTo("camel.component.timer.");
        assertThat(result.camelVersion()).isNotNull();
        assertThat(result.componentProperties()).isNotEmpty();
        assertThat(result.endpointProperties()).isNotEmpty();
        assertThat(result.summary().componentOptionsCount()).isEqualTo(result.componentProperties().size());
        assertThat(result.summary().endpointOptionsCount()).isEqualTo(result.endpointProperties().size());
    }

    @Test
    void componentPropertyKeysHaveExpectedPrefix() {
        ComponentPropertiesTools tools = createTools();

        ComponentPropertiesTools.ComponentPropertiesResult result
                = tools.camel_component_properties("timer", null, null, null);

        assertThat(result.componentProperties())
                .allSatisfy(p -> assertThat(p.key()).startsWith("camel.component.timer."));
        assertThat(result.endpointProperties())
                .allSatisfy(p -> assertThat(p.key()).startsWith("camel.component.timer."));
    }

    @Test
    void endpointPropertiesIncludePathOptions() {
        ComponentPropertiesTools tools = createTools();

        ComponentPropertiesTools.ComponentPropertiesResult result
                = tools.camel_component_properties("timer", null, null, null);

        // timer URI syntax is timer:timerName, so timerName should appear as a path option
        assertThat(result.endpointProperties()).anyMatch(p -> "timerName".equals(p.name())
                && "path".equals(p.kind()));
    }

    @Test
    void propertyDescribesType() {
        ComponentPropertiesTools tools = createTools();

        ComponentPropertiesTools.ComponentPropertiesResult result
                = tools.camel_component_properties("timer", null, null, null);

        assertThat(result.componentProperties())
                .allSatisfy(p -> {
                    assertThat(p.name()).isNotBlank();
                    assertThat(p.javaType()).isNotBlank();
                });
    }

    @Test
    void unknownComponentThrows() {
        ComponentPropertiesTools tools = createTools();

        assertThatThrownBy(() -> tools.camel_component_properties("not-a-real-component-xyz", null, null, null))
                .isInstanceOf(ToolCallException.class)
                .hasMessageContaining("not found");
    }

    @Test
    void blankComponentThrows() {
        ComponentPropertiesTools tools = createTools();

        assertThatThrownBy(() -> tools.camel_component_properties("  ", null, null, null))
                .isInstanceOf(ToolCallException.class)
                .hasMessageContaining("required");
    }

    @Test
    void nullComponentThrows() {
        ComponentPropertiesTools tools = createTools();

        assertThatThrownBy(() -> tools.camel_component_properties(null, null, null, null))
                .isInstanceOf(ToolCallException.class)
                .hasMessageContaining("required");
    }
}
