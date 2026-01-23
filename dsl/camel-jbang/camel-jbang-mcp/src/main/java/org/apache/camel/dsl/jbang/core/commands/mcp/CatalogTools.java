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
import java.util.stream.Collectors;

import jakarta.enterprise.context.ApplicationScoped;

import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import io.quarkiverse.mcp.server.ToolCallException;
import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.catalog.DefaultCamelCatalog;
import org.apache.camel.dsl.jbang.core.common.CatalogLoader;
import org.apache.camel.dsl.jbang.core.common.RuntimeType;
import org.apache.camel.tooling.model.ComponentModel;
import org.apache.camel.tooling.model.DataFormatModel;
import org.apache.camel.tooling.model.EipModel;
import org.apache.camel.tooling.model.LanguageModel;

/**
 * MCP Tools for querying the Camel Catalog using Quarkus MCP Server.
 */
@ApplicationScoped
public class CatalogTools {

    private CamelCatalog catalog;

    public CatalogTools() {
        this.catalog = new DefaultCamelCatalog(true);
    }

    /**
     * Tool to list available Camel components.
     */
    @Tool(description = "List available Camel components from the catalog. " +
                        "Returns component name, description, and labels. " +
                        "Use filter to search by name, label to filter by category.")
    public ComponentListResult camel_catalog_components(
            @ToolArg(description = "Filter components by name (case-insensitive substring match)") String filter,
            @ToolArg(description = "Filter by category label (e.g., cloud, messaging, database, file)") String label,
            @ToolArg(description = "Maximum number of results to return (default: 50)") Integer limit,
            @ToolArg(description = "Runtime type: main, spring-boot, or quarkus (default: main)") String runtime,
            @ToolArg(description = "Specific Camel version to query (e.g., 4.4.0). If not specified, uses the default catalog version.") String camelVersion) {

        int maxResults = limit != null ? limit : 50;

        try {
            CamelCatalog cat = loadCatalog(runtime, camelVersion);

            List<ComponentInfo> components = findComponentNames(cat).stream()
                    .map(cat::componentModel)
                    .filter(m -> m != null)
                    .filter(m -> matchesFilter(m.getScheme(), m.getTitle(), m.getDescription(), filter))
                    .filter(m -> matchesLabel(m.getLabel(), label))
                    .limit(maxResults)
                    .map(this::toComponentInfo)
                    .collect(Collectors.toList());

            return new ComponentListResult(components.size(), cat.getCatalogVersion(), components);
        } catch (Exception e) {
            throw new ToolCallException("Failed to list components: " + e.getMessage(), e);
        }
    }

    /**
     * Tool to get detailed documentation for a specific component.
     */
    @Tool(description = "Get detailed documentation for a Camel component including all options, " +
                        "endpoint parameters, and usage examples.")
    public ComponentDetailResult camel_catalog_component_doc(
            @ToolArg(description = "Component name (e.g., kafka, http, file, timer)") String component,
            @ToolArg(description = "Runtime type: main, spring-boot, or quarkus (default: main)") String runtime,
            @ToolArg(description = "Specific Camel version to query (e.g., 4.4.0). If not specified, uses the default catalog version.") String camelVersion) {

        if (component == null || component.isBlank()) {
            throw new ToolCallException("Component name is required", null);
        }

        try {
            CamelCatalog cat = loadCatalog(runtime, camelVersion);
            ComponentModel model = cat.componentModel(component);
            if (model == null) {
                throw new ToolCallException("Component not found: " + component, null);
            }

            return toComponentDetailResult(model);
        } catch (ToolCallException e) {
            throw e;
        } catch (Exception e) {
            throw new ToolCallException("Failed to get component doc: " + e.getMessage(), e);
        }
    }

    /**
     * Tool to list data formats.
     */
    @Tool(description = "List available Camel data formats for marshalling/unmarshalling " +
                        "(e.g., json, xml, csv, avro, protobuf).")
    public DataFormatListResult camel_catalog_dataformats(
            @ToolArg(description = "Filter by name") String filter,
            @ToolArg(description = "Maximum results (default: 50)") Integer limit) {

        int maxResults = limit != null ? limit : 50;

        try {
            List<DataFormatInfo> dataFormats = catalog.findDataFormatNames().stream()
                    .map(catalog::dataFormatModel)
                    .filter(m -> m != null)
                    .filter(m -> matchesFilter(m.getName(), m.getTitle(), m.getDescription(), filter))
                    .limit(maxResults)
                    .map(this::toDataFormatInfo)
                    .collect(Collectors.toList());

            return new DataFormatListResult(dataFormats.size(), dataFormats);
        } catch (Exception e) {
            throw new ToolCallException("Failed to list data formats: " + e.getMessage(), e);
        }
    }

    /**
     * Tool to list expression languages.
     */
    @Tool(description = "List available Camel expression languages " +
                        "(e.g., simple, jsonpath, xpath, groovy, jq).")
    public LanguageListResult camel_catalog_languages(
            @ToolArg(description = "Filter by name") String filter) {

        try {
            List<LanguageInfo> languages = catalog.findLanguageNames().stream()
                    .map(catalog::languageModel)
                    .filter(m -> m != null)
                    .filter(m -> matchesFilter(m.getName(), m.getTitle(), m.getDescription(), filter))
                    .map(this::toLanguageInfo)
                    .collect(Collectors.toList());

            return new LanguageListResult(languages.size(), languages);
        } catch (Exception e) {
            throw new ToolCallException("Failed to list languages: " + e.getMessage(), e);
        }
    }

    /**
     * Tool to list EIPs (Enterprise Integration Patterns).
     */
    @Tool(description = "List Camel Enterprise Integration Patterns (EIPs) like split, aggregate, " +
                        "filter, choice, multicast, circuit-breaker, etc.")
    public EipListResult camel_catalog_eips(
            @ToolArg(description = "Filter by name") String filter,
            @ToolArg(description = "Filter by category (e.g., routing, transformation, error handling)") String label) {

        try {
            List<EipInfo> eips = catalog.findModelNames().stream()
                    .map(catalog::eipModel)
                    .filter(m -> m != null)
                    .filter(m -> matchesFilter(m.getName(), m.getTitle(), m.getDescription(), filter))
                    .filter(m -> matchesLabel(m.getLabel(), label))
                    .map(this::toEipInfo)
                    .collect(Collectors.toList());

            return new EipListResult(eips.size(), eips);
        } catch (Exception e) {
            throw new ToolCallException("Failed to list EIPs: " + e.getMessage(), e);
        }
    }

    /**
     * Tool to get detailed documentation for a specific EIP.
     */
    @Tool(description = "Get detailed documentation for a Camel EIP (Enterprise Integration Pattern).")
    public EipDetailResult camel_catalog_eip_doc(
            @ToolArg(description = "EIP name (e.g., split, aggregate, choice, filter)") String eip) {

        if (eip == null || eip.isBlank()) {
            throw new ToolCallException("EIP name is required", null);
        }

        EipModel model = catalog.eipModel(eip);
        if (model == null) {
            throw new ToolCallException("EIP not found: " + eip, null);
        }

        return toEipDetailResult(model);
    }

    // Catalog loading

    private CamelCatalog loadCatalog(String runtime, String camelVersion) throws Exception {
        // If a specific version is requested, load that version's catalog
        if (camelVersion != null && !camelVersion.isBlank()) {
            RuntimeType runtimeType = resolveRuntime(runtime);
            if (runtimeType == RuntimeType.springBoot) {
                return CatalogLoader.loadSpringBootCatalog(null, camelVersion, true);
            } else if (runtimeType == RuntimeType.quarkus) {
                return CatalogLoader.loadQuarkusCatalog(null, camelVersion, null, true);
            } else {
                return CatalogLoader.loadCatalog(null, camelVersion, true);
            }
        }

        // No specific version, use runtime-specific catalog or default
        if (runtime == null || runtime.isBlank() || "main".equalsIgnoreCase(runtime)) {
            return catalog;
        }

        RuntimeType runtimeType = RuntimeType.fromValue(runtime);
        if (runtimeType == RuntimeType.springBoot) {
            return CatalogLoader.loadSpringBootCatalog(null, null, true);
        } else if (runtimeType == RuntimeType.quarkus) {
            return CatalogLoader.loadQuarkusCatalog(null, RuntimeType.QUARKUS_VERSION, null, true);
        }

        return catalog;
    }

    private RuntimeType resolveRuntime(String runtime) {
        if (runtime == null || runtime.isBlank() || "main".equalsIgnoreCase(runtime)) {
            return RuntimeType.main;
        }
        return RuntimeType.fromValue(runtime);
    }

    private static List<String> findComponentNames(CamelCatalog catalog) {
        List<String> answer = catalog.findComponentNames();
        List<String> copy = new ArrayList<>(answer);
        copy.removeIf(String::isBlank);
        return copy;
    }

    // Helper methods

    private boolean matchesFilter(String name, String title, String description, String filter) {
        if (filter == null || filter.isBlank()) {
            return true;
        }
        String lowerFilter = filter.toLowerCase();
        return (name != null && name.toLowerCase().contains(lowerFilter))
                || (title != null && title.toLowerCase().contains(lowerFilter))
                || (description != null && description.toLowerCase().contains(lowerFilter));
    }

    private boolean matchesLabel(String labels, String labelFilter) {
        if (labelFilter == null || labelFilter.isBlank()) {
            return true;
        }
        if (labels == null) {
            return false;
        }
        return labels.toLowerCase().contains(labelFilter.toLowerCase());
    }

    // Mapping methods

    private ComponentInfo toComponentInfo(ComponentModel model) {
        return new ComponentInfo(
                model.getScheme(),
                model.getTitle(),
                model.getDescription(),
                model.getLabel(),
                model.isDeprecated(),
                model.getSupportLevel() != null ? model.getSupportLevel().name() : null);
    }

    private ComponentDetailResult toComponentDetailResult(ComponentModel model) {
        List<OptionInfo> componentOptions = new ArrayList<>();
        if (model.getComponentOptions() != null) {
            model.getComponentOptions().forEach(opt -> componentOptions.add(new OptionInfo(
                    opt.getName(),
                    opt.getDescription(),
                    opt.getType(),
                    opt.isRequired(),
                    opt.getDefaultValue() != null ? opt.getDefaultValue().toString() : null,
                    null)));
        }

        List<OptionInfo> endpointOptions = new ArrayList<>();
        if (model.getEndpointOptions() != null) {
            model.getEndpointOptions().forEach(opt -> endpointOptions.add(new OptionInfo(
                    opt.getName(),
                    opt.getDescription(),
                    opt.getType(),
                    opt.isRequired(),
                    opt.getDefaultValue() != null ? opt.getDefaultValue().toString() : null,
                    opt.getGroup())));
        }

        return new ComponentDetailResult(
                model.getScheme(),
                model.getTitle(),
                model.getDescription(),
                model.getLabel(),
                model.isDeprecated(),
                model.getSupportLevel() != null ? model.getSupportLevel().name() : null,
                model.getGroupId(),
                model.getArtifactId(),
                model.getVersion(),
                model.getSyntax(),
                model.isAsync(),
                model.isConsumerOnly(),
                model.isProducerOnly(),
                componentOptions,
                endpointOptions);
    }

    private DataFormatInfo toDataFormatInfo(DataFormatModel model) {
        return new DataFormatInfo(
                model.getName(),
                model.getTitle(),
                model.getDescription(),
                model.isDeprecated());
    }

    private LanguageInfo toLanguageInfo(LanguageModel model) {
        return new LanguageInfo(
                model.getName(),
                model.getTitle(),
                model.getDescription());
    }

    private EipInfo toEipInfo(EipModel model) {
        return new EipInfo(
                model.getName(),
                model.getTitle(),
                model.getDescription(),
                model.getLabel());
    }

    private EipDetailResult toEipDetailResult(EipModel model) {
        List<OptionInfo> options = new ArrayList<>();
        if (model.getOptions() != null) {
            model.getOptions().forEach(opt -> options.add(new OptionInfo(
                    opt.getName(),
                    opt.getDescription(),
                    opt.getType(),
                    opt.isRequired(),
                    opt.getDefaultValue() != null ? opt.getDefaultValue().toString() : null,
                    null)));
        }

        return new EipDetailResult(
                model.getName(),
                model.getTitle(),
                model.getDescription(),
                model.getLabel(),
                options);
    }

    // Result record classes for Jackson serialization

    public record ComponentListResult(int count, String camelVersion, List<ComponentInfo> components) {
    }

    public record ComponentInfo(String name, String title, String description, String label,
            boolean deprecated, String supportLevel) {
    }

    public record ComponentDetailResult(String name, String title, String description, String label,
            boolean deprecated, String supportLevel, String groupId, String artifactId,
            String version, String syntax, boolean async, boolean consumerOnly, boolean producerOnly,
            List<OptionInfo> componentOptions, List<OptionInfo> endpointOptions) {
    }

    public record OptionInfo(String name, String description, String type, boolean required,
            String defaultValue, String group) {
    }

    public record DataFormatListResult(int count, List<DataFormatInfo> dataFormats) {
    }

    public record DataFormatInfo(String name, String title, String description, boolean deprecated) {
    }

    public record LanguageListResult(int count, List<LanguageInfo> languages) {
    }

    public record LanguageInfo(String name, String title, String description) {
    }

    public record EipListResult(int count, List<EipInfo> eips) {
    }

    public record EipInfo(String name, String title, String description, String label) {
    }

    public record EipDetailResult(String name, String title, String description, String label,
            List<OptionInfo> options) {
    }
}
