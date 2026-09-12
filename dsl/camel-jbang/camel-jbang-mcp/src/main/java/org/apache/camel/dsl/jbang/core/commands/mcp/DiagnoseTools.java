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

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import io.quarkiverse.mcp.server.ToolCallException;
import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.dsl.jbang.core.commands.ai.ErrorDiagnoser;
import org.apache.camel.dsl.jbang.core.commands.ai.ToolRegistry;
import org.apache.camel.util.json.JsonObject;

/**
 * MCP Tool for diagnosing Camel errors from stack traces or error messages.
 * <p>
 * Accepts a Camel stack trace or error message and returns the likely component/EIP involved, common causes, links to
 * relevant documentation, and suggested fixes.
 */
@McpSecured
@ApplicationScoped
public class DiagnoseTools {

    @Inject
    CatalogService catalogService;

    /**
     * Tool to diagnose Camel errors from stack traces or error messages: a thin wrapper over the shared
     * {@code camel_error_diagnose} tool of the {@link ToolRegistry} (CAMEL-24695), so the TUI answers the same.
     */
    @Tool(annotations = @Tool.Annotations(readOnlyHint = true, destructiveHint = false, openWorldHint = false),
          description = "Diagnose a Camel error from a stack trace or error message. "
                        + "Returns the identified component/EIP involved, common causes for the error, "
                        + "links to relevant Camel documentation, and suggested fixes. "
                        + "Covers the most common Camel exceptions including NoSuchEndpointException, "
                        + "ResolveEndpointFailedException, FailedToCreateRouteException, and more.")
    public JsonObject camel_error_diagnose(
            @ToolArg(description = "The Camel stack trace or error message to diagnose") String error,
            @ToolArg(description = ToolArgDocs.RUNTIME) String runtime,
            @ToolArg(description = ToolArgDocs.CAMEL_VERSION) String camelVersion,
            @ToolArg(description = ToolArgDocs.PLATFORM_BOM) String platformBom) {

        if (error == null || error.isBlank()) {
            throw new ToolCallException("Error message or stack trace is required", null);
        }
        try {
            CamelCatalog catalog = catalogService.loadCatalog(runtime, camelVersion, platformBom);
            return ErrorDiagnoser.diagnose(error, catalog);
        } catch (ToolCallException e) {
            throw e;
        } catch (Throwable e) {
            throw new ToolCallException(
                    "Failed to diagnose error (" + e.getClass().getName() + "): " + e.getMessage(), null);
        }
    }
}
