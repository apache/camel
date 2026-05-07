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
package org.apache.camel.component.springai.tools;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.camel.Category;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.springai.tools.spec.CamelToolExecutorCache;
import org.apache.camel.component.springai.tools.spec.CamelToolSpecification;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.DefaultEndpoint;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.ai.tool.metadata.ToolMetadata;

import static org.apache.camel.component.springai.tools.SpringAiTools.SCHEME;

@UriEndpoint(firstVersion = "4.17.0", scheme = SCHEME,
             title = "Spring AI Tools",
             syntax = "spring-ai-tools:toolId",
             category = { Category.AI })
public class SpringAiToolsEndpoint extends DefaultEndpoint {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Metadata(required = true)
    @UriPath(description = "The tool id")
    private final String toolId;

    @Metadata(required = true)
    @UriParam(description = "The tags for the tools")
    private String tags;

    @UriParam
    private SpringAiToolsConfiguration configuration;

    @Metadata(label = "consumer")
    @UriParam(description = "Tool description")
    private String description;

    @Metadata(label = "consumer")
    @UriParam(description = "Tool name")
    private String name;

    @Metadata(label = "consumer")
    @UriParam(description = "List of Tool parameters with optional metadata. "
                            + "Format: parameter.<name>=<type>, parameter.<name>.description=<text>, "
                            + "parameter.<name>.required=<true|false>, parameter.<name>.enum=<value1,value2>. "
                            + "Example: parameter.location=string, parameter.location.description=The city and state, "
                            + "parameter.location.required=true, parameter.unit.enum=C,F",
              prefix = "parameter.", multiValue = true)
    private Map<String, String> parameters;

    @Metadata(label = "consumer")
    @UriParam(description = "Input type class for the tool")
    private Class<?> inputType;

    @Metadata(label = "consumer")
    @UriParam(description = "Whether the tool result should be returned directly or passed back to the model. "
                            + "Default is false, meaning the result is passed back to the model for further processing.",
              defaultValue = "false")
    private boolean returnDirect;

    public SpringAiToolsEndpoint(String uri, SpringAiToolsComponent component, String toolId, String tags,
                                 SpringAiToolsConfiguration configuration) {
        super(uri, component);
        this.toolId = toolId;
        this.tags = tags;
        this.configuration = configuration;
    }

    @Override
    public Producer createProducer() throws Exception {
        throw new UnsupportedOperationException(
                "The spring-ai-tools component does not support producer mode. "
                                                + "Use the spring-ai-chat component with tags parameter to invoke tools. "
                                                + "Example: spring-ai-chat:chat?tags=weather&chatClient=#chatClient");
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        if (description == null) {
            throw new IllegalArgumentException(
                    "In order to use the spring-ai-tools component as a consumer, you need to specify at least description");
        }

        final String toolName;
        if (name != null) {
            toolName = name;
        } else {
            toolName = toolId;
        }

        final SpringAiToolsConsumer springAiToolsConsumer = new SpringAiToolsConsumer(this, processor);
        configureConsumer(springAiToolsConsumer);

        // Create a function that executes the Camel route
        java.util.function.Function<java.util.Map<String, Object>, String> function = args -> {
            try {
                org.apache.camel.Exchange exchange = createExchange();
                // Set arguments as headers
                for (java.util.Map.Entry<String, Object> entry : args.entrySet()) {
                    exchange.getMessage().setHeader(entry.getKey(), entry.getValue());
                }

                // Execute the consumer route
                springAiToolsConsumer.getProcessor().process(exchange);

                // Return the result
                return exchange.getIn().getBody(String.class);
            } catch (Exception e) {
                throw new RuntimeException("Error executing tool", e);
            }
        };

        // Build the tool callback using FunctionToolCallback
        FunctionToolCallback.Builder builder = FunctionToolCallback.builder(toolName, function)
                .description(description);

        if (inputType != null) {
            builder.inputType(inputType);
        } else if (parameters != null && !parameters.isEmpty()) {
            // Build JSON schema from parameters map
            String inputSchema = buildJsonSchemaFromParameters(parameters);
            builder.inputSchema(inputSchema);
            builder.inputType(java.util.Map.class);
        } else {
            builder.inputType(java.util.Map.class);
        }

        // Configure tool metadata
        if (returnDirect) {
            builder.toolMetadata(ToolMetadata.builder().returnDirect(true).build());
        }

        ToolCallback toolCallback = builder.build();

        CamelToolSpecification camelToolSpecification
                = new CamelToolSpecification(toolCallback, springAiToolsConsumer);
        final CamelToolExecutorCache executorCache = CamelToolExecutorCache.getInstance();

        String[] splitTags = TagsHelper.splitTags(tags);
        for (String tag : splitTags) {
            executorCache.put(tag, camelToolSpecification);
        }

        return camelToolSpecification.getConsumer();
    }

    /**
     * A freely named tool ID (prefer to use something unique)
     *
     * @return
     */
    public String getToolId() {
        return toolId;
    }

    /**
     * The tool configuration
     *
     * @return
     */
    public SpringAiToolsConfiguration getConfiguration() {
        return configuration;
    }

    /**
     * A description of the tool. This is passed to the LLM, so it should be descriptive of the tool capabilities
     *
     * @return
     */
    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * The tool name. This is passed to the LLM, so it should conform to any LLM restrictions.
     *
     * @return
     */
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    /**
     * The input parameters for the tool
     *
     * @return
     */
    public Map<String, String> getParameters() {
        return parameters;
    }

    public void setParameters(Map<String, String> parameters) {
        this.parameters = parameters;
    }

    public void setTags(String tags) {
        this.tags = tags;
    }

    /**
     * The tags associated with the tool
     *
     * @return
     */
    public String getTags() {
        return tags;
    }

    public Class<?> getInputType() {
        return inputType;
    }

    public void setInputType(Class<?> inputType) {
        this.inputType = inputType;
    }

    /**
     * Whether the tool result should be returned directly or passed back to the model
     *
     * @return
     */
    public boolean isReturnDirect() {
        return returnDirect;
    }

    public void setReturnDirect(boolean returnDirect) {
        this.returnDirect = returnDirect;
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();

        CamelToolExecutorCache.getInstance().getTools().clear();
    }

    /**
     * Builds a JSON schema from the parameters map in the format expected by openai function call API
     * https://platform.openai.com/docs/guides/function-calling.
     * <p>
     * Supports the following parameter formats:
     * <ul>
     * <li>parameter.&lt;name&gt;=&lt;type&gt; - defines the parameter type (e.g., parameter.location=string)</li>
     * <li>parameter.&lt;name&gt;.description=&lt;text&gt; - defines the parameter description</li>
     * <li>parameter.&lt;name&gt;.required=&lt;true|false&gt; - defines if parameter is required (default: false)</li>
     * <li>parameter.&lt;name&gt;.enum=&lt;value1,value2,...&gt; - defines allowed values (comma-separated)</li>
     * </ul>
     *
     * @param  parameters the map of parameter configurations
     * @return            JSON schema string conforming to JSON Schema Draft 2020-12
     */
    private String buildJsonSchemaFromParameters(Map<String, String> parameters) {
        try {
            // Parse parameters into structured format
            Map<String, ParameterMetadata> paramMetadata = parseParameterMetadata(parameters);

            // Build JSON schema
            ObjectNode schema = OBJECT_MAPPER.createObjectNode();
            schema.put("type", "object");

            ObjectNode properties = schema.putObject("properties");
            List<String> requiredParams = new ArrayList<>();

            for (Map.Entry<String, ParameterMetadata> entry : paramMetadata.entrySet()) {
                String paramName = entry.getKey();
                ParameterMetadata metadata = entry.getValue();

                ObjectNode property = properties.putObject(paramName);
                property.put("type", mapTypeToJsonSchemaType(metadata.type));

                if (metadata.description != null) {
                    property.put("description", metadata.description);
                }

                if (metadata.enumValues != null && !metadata.enumValues.isEmpty()) {
                    ArrayNode enumArray = property.putArray("enum");
                    for (String enumValue : metadata.enumValues) {
                        enumArray.add(enumValue.trim());
                    }
                }

                if (metadata.required) {
                    requiredParams.add(paramName);
                }
            }

            // Add required array if there are required parameters
            if (!requiredParams.isEmpty()) {
                ArrayNode requiredArray = schema.putArray("required");
                for (String requiredParam : requiredParams) {
                    requiredArray.add(requiredParam);
                }
            }

            return OBJECT_MAPPER.writeValueAsString(schema);
        } catch (Exception e) {
            throw new RuntimeException("Error building JSON schema from parameters", e);
        }
    }

    /**
     * Parses the flat parameter map into structured metadata.
     * <p>
     * Handles parameter configurations like:
     * <ul>
     * <li>parameter.location=string</li>
     * <li>parameter.location.description=The city and state</li>
     * <li>parameter.location.required=true</li>
     * <li>parameter.location.enum=C,F</li>
     * </ul>
     *
     * @param  parameters the flat parameter map
     * @return            map of parameter names to their metadata
     */
    private Map<String, ParameterMetadata> parseParameterMetadata(Map<String, String> parameters) {
        Map<String, ParameterMetadata> metadata = new HashMap<>();

        for (Map.Entry<String, String> entry : parameters.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();

            if (key.contains(".")) {
                // Handle nested properties like "location.description"
                String[] parts = key.split("\\.", 2);
                String paramName = parts[0];
                String propertyName = parts[1];

                ParameterMetadata meta = metadata.computeIfAbsent(paramName, k -> new ParameterMetadata());

                switch (propertyName) {
                    case "description":
                        meta.description = value;
                        break;
                    case "required":
                        meta.required = Boolean.parseBoolean(value);
                        break;
                    case "enum":
                        meta.enumValues = List.of(value.split(","));
                        break;
                    default:
                        // Ignore unknown properties
                        break;
                }
            } else {
                // Handle direct parameter type like "location=string"
                ParameterMetadata meta = metadata.computeIfAbsent(key, k -> new ParameterMetadata());
                meta.type = value;
            }
        }

        return metadata;
    }

    /**
     * Internal class to hold parameter metadata.
     */
    private static class ParameterMetadata {
        String type = "string"; // default type
        String description;
        boolean required = false; // default not required
        List<String> enumValues;
    }

    /**
     * Maps Camel parameter types to JSON schema types.
     *
     * @param  type the Camel parameter type
     * @return      the corresponding JSON schema type
     */
    private String mapTypeToJsonSchemaType(String type) {
        return switch (type.toLowerCase()) {
            case "string" -> "string";
            case "integer", "int", "long" -> "integer";
            case "number", "double", "float" -> "number";
            case "boolean", "bool" -> "boolean";
            default -> "string"; // fallback for unknown types
        };
    }
}
