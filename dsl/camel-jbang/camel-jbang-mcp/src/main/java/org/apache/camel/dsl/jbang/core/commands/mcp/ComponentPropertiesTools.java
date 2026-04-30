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

import java.util.ArrayList;
import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import io.quarkiverse.mcp.server.ToolCallException;
import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.tooling.model.BaseOptionModel;
import org.apache.camel.tooling.model.ComponentModel;

/**
 * MCP Tool that lists valid {@code application.properties} keys for a Camel component, so AI agents can build
 * configuration without having to parse the full component documentation.
 */
@ApplicationScoped
public class ComponentPropertiesTools {

    @Inject
    CatalogService catalogService;

    /**
     * Tool to list configuration properties for a Camel component.
     */
    @Tool(annotations = @Tool.Annotations(readOnlyHint = true, destructiveHint = false, openWorldHint = false),
          description = "List valid configuration property keys for a Camel component, in the form camel.component.<scheme>.<name>. "
                        +
                        "For each property the tool returns the option name, type, default value, required flag, deprecated flag, "
                        +
                        "secret flag, enum choices and description. Endpoint options are also returned, since they can be set as "
                        +
                        "component-level defaults via the same prefix in application.properties.")
    public ComponentPropertiesResult camel_component_properties(
            @ToolArg(description = "Component name / scheme (e.g., kafka, http, file, timer)") String component,
            @ToolArg(description = "Runtime type: main, spring-boot, or quarkus (default: main)") String runtime,
            @ToolArg(description = "Version to query. For Main or Spring Boot: the Camel version (e.g., 4.17.0). "
                                   + "For quarkus: the Quarkus Platform version. "
                                   + "If not specified, uses the default catalog version.") String camelVersion,
            @ToolArg(description = "Platform BOM coordinates in GAV format (groupId:artifactId:version). "
                                   + "When provided, overrides camelVersion.") String platformBom) {

        if (component == null || component.isBlank()) {
            throw new ToolCallException("Component name is required", null);
        }

        try {
            CamelCatalog catalog = catalogService.loadCatalog(runtime, camelVersion, platformBom);
            ComponentModel model = catalog.componentModel(component);
            if (model == null) {
                throw new ToolCallException("Component not found: " + component, null);
            }

            String prefix = "camel.component." + model.getScheme() + ".";

            List<ComponentPropertyInfo> componentProperties = new ArrayList<>();
            if (model.getComponentOptions() != null) {
                for (ComponentModel.ComponentOptionModel opt : model.getComponentOptions()) {
                    componentProperties.add(toPropertyInfo(prefix, opt));
                }
            }

            List<ComponentPropertyInfo> endpointProperties = new ArrayList<>();
            if (model.getEndpointOptions() != null) {
                for (ComponentModel.EndpointOptionModel opt : model.getEndpointOptions()) {
                    endpointProperties.add(toPropertyInfo(prefix, opt));
                }
            }

            ComponentPropertiesSummary summary = new ComponentPropertiesSummary(
                    componentProperties.size(), endpointProperties.size());

            return new ComponentPropertiesResult(
                    model.getScheme(),
                    model.getTitle(),
                    model.getSyntax(),
                    catalog.getCatalogVersion(),
                    prefix,
                    summary,
                    componentProperties,
                    endpointProperties);
        } catch (ToolCallException e) {
            throw e;
        } catch (Throwable e) {
            throw new ToolCallException(
                    "Failed to list properties for component '" + component + "' (" + e.getClass().getName() + "): "
                                        + e.getMessage(),
                    null);
        }
    }

    private ComponentPropertyInfo toPropertyInfo(String prefix, BaseOptionModel option) {
        String name = option.getName();
        Object defaultValue = option.getDefaultValue();
        List<String> enums = option.getEnums() != null ? new ArrayList<>(option.getEnums()) : List.of();

        return new ComponentPropertyInfo(
                prefix + name,
                name,
                option.getKind(),
                option.getGroup(),
                option.getType(),
                option.getJavaType(),
                option.isRequired(),
                option.isDeprecated(),
                option.isSecret(),
                defaultValue != null ? defaultValue.toString() : null,
                option.getDescription(),
                enums);
    }

    // Result record classes for Jackson serialization

    public record ComponentPropertiesResult(String scheme, String title, String syntax, String camelVersion,
            String prefix, ComponentPropertiesSummary summary, List<ComponentPropertyInfo> componentProperties,
            List<ComponentPropertyInfo> endpointProperties) {
    }

    public record ComponentPropertiesSummary(int componentOptionsCount, int endpointOptionsCount) {
    }

    public record ComponentPropertyInfo(String key, String name, String kind, String group, String type,
            String javaType, boolean required, boolean deprecated, boolean secret, String defaultValue,
            String description, List<String> enumChoices) {
    }
}
