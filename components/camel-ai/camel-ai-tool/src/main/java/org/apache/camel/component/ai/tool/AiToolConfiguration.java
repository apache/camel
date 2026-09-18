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
package org.apache.camel.component.ai.tool;

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spi.Configurer;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;

/**
 * Configuration for the {@link AiToolComponent}: tool description, tags, and parameter metadata.
 *
 * @since 4.22
 */
@Configurer
@UriParams
public class AiToolConfiguration implements Cloneable {

    @Metadata(label = "consumer")
    @UriParam(description = "Comma-separated list of tags used to group tools. "
                            + "Producers filter the registry by these tags to select which tools to expose to the LLM. "
                            + "When omitted, the tool goes into a default pool available to all producers.")
    private String tags;

    @Metadata(label = "consumer")
    @UriParam(description = "Human-readable description of what this tool does. "
                            + "Passed verbatim to the LLM; be precise and action-oriented. "
                            + "When omitted, defaults to the tool name.")
    private String description;

    @Metadata(label = "consumer")
    @UriParam(description = "Tool input parameters. "
                            + "Format: parameter.NAME=TYPE, parameter.NAME.description=TEXT, "
                            + "parameter.NAME.required=true or false, parameter.NAME.enum=val1,val2. "
                            + "Supported types: string, integer, number, boolean. "
                            + "Mutually exclusive with argSchema.",
              prefix = "parameter.", multiValue = true)
    private Map<String, String> parameters;

    @UriParam(description = "Raw JSON Schema for tool input parameters. Supports inline JSON and resource "
                            + "references (classpath:, file:, resource:). Mutually exclusive with the parameter "
                            + "multi-value options. Use for nested objects, arrays, oneOf, and other complex schemas.")
    @Metadata(label = "consumer", supportFileReference = true, largeInput = true, inputLanguage = "json")
    private String argSchema;

    @Metadata(label = "consumer")
    @UriParam(description = "Tool output schema fields. "
                            + "Format: outputParameter.NAME=TYPE, outputParameter.NAME.description=TEXT. "
                            + "Supported types: string, integer, number, boolean. "
                            + "Mutually exclusive with outputSchema.",
              prefix = "outputParameter.", multiValue = true)
    private Map<String, String> outputParameters;

    @UriParam(description = "Raw JSON Schema describing the tool's structured output. Supports inline JSON and "
                            + "resource references (classpath:, file:, resource:). Mutually exclusive with the "
                            + "outputParameter multi-value options. When declared, the route body is parsed as JSON "
                            + "and exposed as structured content to MCP clients.")
    @Metadata(label = "consumer", supportFileReference = true, largeInput = true, inputLanguage = "json")
    private String outputSchema;

    @Metadata(label = "consumer")
    @UriParam(description = "Optional display title for MCP tool listings. Advisory hint for MCP clients only.")
    private String title;

    @Metadata(label = "consumer")
    @UriParam(description = "MCP hint that the tool only reads data and does not modify state. Advisory for MCP clients; "
                            + "not enforced by Camel.")
    private Boolean readOnlyHint;

    @Metadata(label = "consumer")
    @UriParam(description = "MCP hint that the tool may perform destructive or irreversible updates. Advisory for MCP "
                            + "clients; not enforced by Camel.")
    private Boolean destructiveHint;

    @Metadata(label = "consumer")
    @UriParam(description = "MCP hint that repeating the tool call with the same arguments has no additional effect. "
                            + "Advisory for MCP clients; not enforced by Camel.")
    private Boolean idempotentHint;

    @Metadata(label = "consumer")
    @UriParam(description = "MCP hint that the tool interacts with external systems outside the application's control. "
                            + "Advisory for MCP clients; not enforced by Camel.")
    private Boolean openWorldHint;

    @Metadata(label = "consumer", defaultValue = "false")
    @UriParam(description = "When true, AI producers that support agentic tool loops (such as camel-openai) return "
                            + "this tool's result directly to the caller without sending it back to the model. "
                            + "Also published as an MCP tool annotation when the tool is exposed via camel-mcp-server.")
    private Boolean returnDirect;

    public AiToolConfiguration() {
    }

    public String getTags() {
        return tags;
    }

    public void setTags(String tags) {
        this.tags = tags;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Map<String, String> getParameters() {
        return parameters;
    }

    public void setParameters(Map<String, String> parameters) {
        this.parameters = parameters;
    }

    public String getArgSchema() {
        return argSchema;
    }

    public void setArgSchema(String argSchema) {
        this.argSchema = argSchema;
    }

    public Map<String, String> getOutputParameters() {
        return outputParameters;
    }

    public void setOutputParameters(Map<String, String> outputParameters) {
        this.outputParameters = outputParameters;
    }

    public String getOutputSchema() {
        return outputSchema;
    }

    public void setOutputSchema(String outputSchema) {
        this.outputSchema = outputSchema;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public Boolean getReadOnlyHint() {
        return readOnlyHint;
    }

    public void setReadOnlyHint(Boolean readOnlyHint) {
        this.readOnlyHint = readOnlyHint;
    }

    public Boolean getDestructiveHint() {
        return destructiveHint;
    }

    public void setDestructiveHint(Boolean destructiveHint) {
        this.destructiveHint = destructiveHint;
    }

    public Boolean getIdempotentHint() {
        return idempotentHint;
    }

    public void setIdempotentHint(Boolean idempotentHint) {
        this.idempotentHint = idempotentHint;
    }

    public Boolean getOpenWorldHint() {
        return openWorldHint;
    }

    public void setOpenWorldHint(Boolean openWorldHint) {
        this.openWorldHint = openWorldHint;
    }

    public Boolean getReturnDirect() {
        return returnDirect;
    }

    public void setReturnDirect(Boolean returnDirect) {
        this.returnDirect = returnDirect;
    }

    public AiToolConfiguration copy() {
        try {
            AiToolConfiguration copy = (AiToolConfiguration) super.clone();
            if (this.parameters != null) {
                copy.parameters = new HashMap<>(this.parameters);
            }
            if (this.outputParameters != null) {
                copy.outputParameters = new HashMap<>(this.outputParameters);
            }
            return copy;
        } catch (CloneNotSupportedException e) {
            throw new RuntimeCamelException(e);
        }
    }
}
