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
import java.util.Locale;
import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;

import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import io.quarkiverse.mcp.server.ToolCallException;
import org.apache.camel.dsl.jbang.core.commands.catalog.KameletCatalogHelper;
import org.apache.camel.dsl.jbang.core.commands.catalog.KameletModel;
import org.apache.camel.dsl.jbang.core.commands.catalog.KameletOptionModel;
import org.apache.camel.dsl.jbang.core.common.RuntimeType;

/**
 * MCP Tools for querying the Kamelet Catalog using Quarkus MCP Server.
 */
@ApplicationScoped
public class KameletTools {

    /**
     * Tool to list available Kamelets.
     */
    @Tool(description = "List available Camel Kamelets from the Kamelet Catalog. " +
                        "Returns kamelet name, type (source, sink, action), support level, and description. " +
                        "Use filter to search by name or description, type to filter by category.")
    public KameletListResult camel_catalog_kamelets(
            @ToolArg(description = "Filter kamelets by name or description (case-insensitive substring match)") String filter,
            @ToolArg(description = "Filter by type: source, sink, or action") String type,
            @ToolArg(description = "Maximum number of results to return (default: 50)") Integer limit,
            @ToolArg(description = "Apache Camel Kamelets version. If not specified, uses the default version.") String kameletsVersion) {

        int maxResults = limit != null ? limit : 50;

        try {
            String version = kameletsVersion != null && !kameletsVersion.isBlank()
                    ? kameletsVersion : RuntimeType.KAMELETS_VERSION;

            Map<String, Object> kamelets = KameletCatalogHelper.loadKamelets(version, null);

            List<KameletInfo> result = new ArrayList<>();
            for (Object o : kamelets.values()) {
                KameletModel km = KameletCatalogHelper.createModel(o, false);

                // filter by type
                if (type != null && !type.isBlank()
                        && !type.equalsIgnoreCase(km.type)) {
                    continue;
                }

                // filter by name or description
                if (filter != null && !filter.isBlank()) {
                    String lowerFilter = filter.toLowerCase(Locale.ROOT);
                    boolean matches = (km.name != null && km.name.toLowerCase(Locale.ROOT).contains(lowerFilter))
                            || (km.description != null && km.description.toLowerCase(Locale.ROOT).contains(lowerFilter));
                    if (!matches) {
                        continue;
                    }
                }

                result.add(new KameletInfo(km.name, km.type, km.supportLevel, km.description));

                if (result.size() >= maxResults) {
                    break;
                }
            }

            // sort by name
            result.sort((a, b) -> a.name().compareToIgnoreCase(b.name()));

            return new KameletListResult(result.size(), version, result);
        } catch (Throwable e) {
            throw new ToolCallException(
                    "Failed to list kamelets (" + e.getClass().getName() + "): " + e.getMessage(), null);
        }
    }

    /**
     * Tool to get detailed documentation for a specific Kamelet.
     */
    @Tool(description = "Get detailed documentation for a specific Camel Kamelet including all properties/options, "
                        + "dependencies, and usage information.")
    public KameletDetailResult camel_catalog_kamelet_doc(
            @ToolArg(description = "Kamelet name (e.g., aws-s3-source, kafka-sink, log-action)") String kamelet,
            @ToolArg(description = "Apache Camel Kamelets version. If not specified, uses the default version.") String kameletsVersion) {

        if (kamelet == null || kamelet.isBlank()) {
            throw new ToolCallException("Kamelet name is required", null);
        }

        try {
            String version = kameletsVersion != null && !kameletsVersion.isBlank()
                    ? kameletsVersion : RuntimeType.KAMELETS_VERSION;

            KameletModel km = KameletCatalogHelper.loadKameletModel(kamelet, version, null);
            if (km == null) {
                throw new ToolCallException("Kamelet not found: " + kamelet, null);
            }

            List<KameletOptionInfo> options = new ArrayList<>();
            if (km.properties != null) {
                for (KameletOptionModel om : km.properties.values()) {
                    options.add(new KameletOptionInfo(
                            om.name,
                            om.description,
                            om.type,
                            om.required,
                            om.defaultValue,
                            om.example,
                            om.enumValues));
                }
            }

            return new KameletDetailResult(
                    km.name,
                    km.type,
                    km.supportLevel,
                    km.description,
                    options,
                    km.dependencies);
        } catch (ToolCallException e) {
            throw e;
        } catch (Throwable e) {
            throw new ToolCallException(
                    "Kamelet not found: " + kamelet + " (" + e.getClass().getName() + "): " + e.getMessage(), null);
        }
    }

    // Result record classes for Jackson serialization

    public record KameletListResult(int count, String kameletsVersion, List<KameletInfo> kamelets) {
    }

    public record KameletInfo(String name, String type, String supportLevel, String description) {
    }

    public record KameletDetailResult(String name, String type, String supportLevel, String description,
            List<KameletOptionInfo> options, List<String> dependencies) {
    }

    public record KameletOptionInfo(String name, String description, String type, boolean required,
            String defaultValue, String example, List<String> enumValues) {
    }
}
