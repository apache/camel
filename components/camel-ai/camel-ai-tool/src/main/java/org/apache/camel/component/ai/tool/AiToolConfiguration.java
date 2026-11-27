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
import org.apache.camel.spi.AuthorizationPolicy;
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
    @UriParam(description = "A comma-separated list of tags to register the tool under. An AI producer (such as openai,"
                            + " langchain4j-agent or spring-ai-chat) offers the model the tools of the tags in its own tags"
                            + " option, and the MCP server exposes the tools whose tags match its tag patterns. Without"
                            + " tags, the tool goes into a default pool that is added to every tag a producer selects,"
                            + " and that the MCP server never exposes.")
    private String tags;

    @Metadata(label = "consumer")
    @UriParam(description = "The description of what the tool does, which the model reads as is to decide when to"
                            + " call the tool, and which MCP clients see in tools/list. If not set or blank, the tool"
                            + " name is used.")
    private String description;

    @Metadata(label = "consumer")
    @UriParam(description = "The input parameters of the tool, set as parameter.NAME=TYPE,"
                            + " parameter.NAME.description=TEXT, parameter.NAME.required=true or false and"
                            + " parameter.NAME.enum=VALUE1,VALUE2, from which Camel builds the JSON Schema of the tool"
                            + " input. TYPE is string, integer, number or boolean, and string when not set or not known."
                            + " The route receives each argument as a header named NAME, and a call without a required"
                            + " argument is refused before the route runs. Cannot be combined with argSchema.",
              prefix = "parameter.", multiValue = true)
    private Map<String, String> parameters;

    @UriParam(description = "The JSON Schema of the tool input, inline or loaded from a resource such as"
                            + " classpath:schema.json or file:schema.json, for input that the parameter options cannot"
                            + " describe, such as nested objects, arrays and oneOf. The root must be an object schema"
                            + " with at least one property, and the route receives each top-level argument as a header."
                            + " When the schema does not set additionalProperties, Camel adds additionalProperties:"
                            + " false, so a client that validates the input (such as the MCP server) refuses an"
                            + " undeclared argument. Cannot be combined with the parameter options.")
    @Metadata(label = "consumer", supportFileReference = true, largeInput = true, inputLanguage = "json")
    private String argSchema;

    @Metadata(label = "consumer")
    @UriParam(defaultValue = "false", description = "Whether the route also receives the arguments of the call as a JSON"
                                                    + " object in the message body, as a String. The body holds the same"
                                                    + " arguments as the headers, so undeclared arguments and names that"
                                                    + " start with camel are left out. When false, the route starts"
                                                    + " without a body.")
    private boolean argumentsAsBody;

    @Metadata(label = "consumer")
    @UriParam(description = "The fields of the structured output of the tool, set as outputParameter.NAME=TYPE and"
                            + " outputParameter.NAME.description=TEXT, from which Camel builds the output JSON Schema that"
                            + " MCP clients see in tools/list. TYPE is string, integer, number or boolean. When set, the"
                            + " route must reply with a JSON body, which the MCP server returns as structured content."
                            + " Cannot be combined with outputSchema.",
              prefix = "outputParameter.", multiValue = true)
    private Map<String, String> outputParameters;

    @UriParam(description = "The JSON Schema of the structured output of the tool, inline or loaded from a resource"
                            + " such as classpath:result.json, which MCP clients see in tools/list. When set, the route"
                            + " must reply with a JSON body, which the MCP server returns as structured content. Cannot"
                            + " be combined with the outputParameter options.")
    @Metadata(label = "consumer", supportFileReference = true, largeInput = true, inputLanguage = "json")
    private String outputSchema;

    @Metadata(label = "consumer")
    @UriParam(description = "The human-readable title of the tool, which MCP clients can show in tools/list instead of"
                            + " the name. The AI producers do not send it to the model. If not set, the tool has no"
                            + " title.")
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
    @UriParam(description = "Whether the openai producer ends its tool loop with the result of this tool as the reply,"
                            + " without sending the result back to the model. It does so only when every tool that the"
                            + " model called in that turn sets returnDirect. The other AI producers ignore it, and some"
                            + " MCP server engines publish it to clients as a tool annotation.")
    private Boolean returnDirect;

    @Metadata(label = "consumer", defaultValue = "false")
    @UriParam(description = "When true, this route tool is eligible for tool search and is omitted from the initial "
                            + "model request while the producer has tool search enabled. When false, the tool stays "
                            + "directly visible to the model. Searchability is a visibility policy, not an authorization "
                            + "policy, and has no effect when tool search is disabled.")
    private boolean searchable;

    @Metadata(label = "consumer,security")
    @UriParam(description = "Reference to an org.apache.camel.spi.AuthorizationPolicy used to authorize tool calls "
                            + "before the route runs. Set it on the component to guard every tool route by "
                            + "construction, or per endpoint to override. The policy authorizes on trustworthy input "
                            + "only: the tool name comes from the route (never from model output), and the caller "
                            + "identity is carried as an exchange property set before the agent ran (for example by "
                            + "camel-spiffe or camel-keycloak), which the model cannot set. Authorize on exchange "
                            + "properties or validated tokens only, never on message headers (on a tool route the "
                            + "headers carry the model-controlled tool arguments). A denied call surfaces to the "
                            + "model as a short refusal rather than a stack trace.")
    private AuthorizationPolicy authorizationPolicy;

    @Metadata(label = "consumer,security")
    @UriParam(description = "Comma-separated list of the roles a caller needs to call the tool, for example "
                            + "finance,controlling. Any one of them is enough unless allRolesRequired is set. The roles "
                            + "are checked before the route runs, against the caller's roles in the exchange property "
                            + "CamelAiToolSecurityPrincipalRoles: a call without that property is refused. A value "
                            + "that is set but names no role refuses every call. Set it on the component as the "
                            + "default of every tool route, or per endpoint to replace it.")
    private String allowedRoles;

    @Metadata(label = "consumer,security")
    @UriParam(defaultValue = "false", description = "Whether the caller needs all the roles in allowedRoles, rather "
                                                    + "than any one of them. Has no effect when allowedRoles is not "
                                                    + "set.")
    private boolean allRolesRequired;

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

    public boolean isArgumentsAsBody() {
        return argumentsAsBody;
    }

    public void setArgumentsAsBody(boolean argumentsAsBody) {
        this.argumentsAsBody = argumentsAsBody;
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

    public boolean isSearchable() {
        return searchable;
    }

    public void setSearchable(boolean searchable) {
        this.searchable = searchable;
    }

    public AuthorizationPolicy getAuthorizationPolicy() {
        return authorizationPolicy;
    }

    public void setAuthorizationPolicy(AuthorizationPolicy authorizationPolicy) {
        this.authorizationPolicy = authorizationPolicy;
    }

    public String getAllowedRoles() {
        return allowedRoles;
    }

    public void setAllowedRoles(String allowedRoles) {
        this.allowedRoles = allowedRoles;
    }

    public boolean isAllRolesRequired() {
        return allRolesRequired;
    }

    public void setAllRolesRequired(boolean allRolesRequired) {
        this.allRolesRequired = allRolesRequired;
    }

    /**
     * Copies onto this configuration every value that is set on the other one, and keeps the values of this
     * configuration that the other one does not set. A value is set when it is not null, or, for a boolean option whose
     * default is false, when it is true. A new option must be added here too; AiToolConfigurationOverrideWithTest fails
     * when one is missing.
     */
    void overrideWith(AiToolConfiguration other) {
        if (other.tags != null) {
            tags = other.tags;
        }
        if (other.description != null) {
            description = other.description;
        }
        if (other.parameters != null) {
            parameters = new HashMap<>(other.parameters);
        }
        if (other.argSchema != null) {
            argSchema = other.argSchema;
        }
        if (other.argumentsAsBody) {
            argumentsAsBody = true;
        }
        if (other.outputParameters != null) {
            outputParameters = new HashMap<>(other.outputParameters);
        }
        if (other.outputSchema != null) {
            outputSchema = other.outputSchema;
        }
        if (other.title != null) {
            title = other.title;
        }
        if (other.readOnlyHint != null) {
            readOnlyHint = other.readOnlyHint;
        }
        if (other.destructiveHint != null) {
            destructiveHint = other.destructiveHint;
        }
        if (other.idempotentHint != null) {
            idempotentHint = other.idempotentHint;
        }
        if (other.openWorldHint != null) {
            openWorldHint = other.openWorldHint;
        }
        if (other.returnDirect != null) {
            returnDirect = other.returnDirect;
        }
        if (other.searchable) {
            searchable = true;
        }
        if (other.authorizationPolicy != null) {
            authorizationPolicy = other.authorizationPolicy;
        }
        if (other.allowedRoles != null) {
            allowedRoles = other.allowedRoles;
        }
        if (other.allRolesRequired) {
            allRolesRequired = true;
        }
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
