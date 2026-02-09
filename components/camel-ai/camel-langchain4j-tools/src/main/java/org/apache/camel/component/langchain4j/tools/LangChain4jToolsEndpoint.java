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
package org.apache.camel.component.langchain4j.tools;

import java.util.List;
import java.util.Map;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonBooleanSchema;
import dev.langchain4j.model.chat.request.json.JsonIntegerSchema;
import dev.langchain4j.model.chat.request.json.JsonNumberSchema;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonSchemaElement;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;
import org.apache.camel.Category;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.langchain4j.tools.spec.CamelSimpleToolParameter;
import org.apache.camel.component.langchain4j.tools.spec.CamelToolExecutorCache;
import org.apache.camel.component.langchain4j.tools.spec.CamelToolSpecification;
import org.apache.camel.component.langchain4j.tools.spec.NamedJsonSchemaProperty;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.DefaultEndpoint;
import org.apache.camel.util.StringHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.component.langchain4j.tools.LangChain4jTools.SCHEME;

@UriEndpoint(firstVersion = "4.8.0", scheme = SCHEME,
             title = "LangChain4j Tools",
             syntax = "langchain4j-tools:toolId",
             category = { Category.AI })
public class LangChain4jToolsEndpoint extends DefaultEndpoint {

    private static final Logger LOG = LoggerFactory.getLogger(LangChain4jToolsEndpoint.class);

    @Metadata(required = true)
    @UriPath(description = "The tool id")
    private final String toolId;

    @Metadata(required = true)
    @UriParam(description = "The tags for the tools")
    private String tags;

    @UriParam
    private LangChain4jToolsConfiguration configuration;

    @Metadata(label = "consumer")
    @UriParam(description = "Tool description")
    private String description;

    @Metadata(label = "consumer")
    @UriParam(description = "Tool name")
    private String name;

    @Metadata(label = "consumer")
    @UriParam(description = "List of Tool parameters in the form of parameter.<name>=<type>", prefix = "parameter.",
              multiValue = true)
    private Map<String, String> parameters;

    @Metadata(label = "consumer,advanced")
    @UriParam(description = "Tool's Camel Parameters, programmatically define Tool description and parameters")
    private CamelSimpleToolParameter camelToolParameter;

    @Metadata(label = "consumer", defaultValue = "true")
    @UriParam(description = "Whether the tool is automatically exposed to the LLM. When false, the tool is added to a searchable list and can be discovered via the tool-search-tool",
              defaultValue = "true")
    private boolean exposed = true;

    // Track the tool specification created by this endpoint for proper cleanup
    private CamelToolSpecification camelToolSpecification;

    public LangChain4jToolsEndpoint(String uri, LangChain4jToolsComponent component, String toolId, String tags,
                                    LangChain4jToolsConfiguration configuration) {
        super(uri, component);
        this.toolId = toolId;
        this.tags = tags;
        this.configuration = configuration;
    }

    @Override
    public Producer createProducer() throws Exception {
        return new LangChain4jToolsProducer(this);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        ToolSpecification.Builder toolSpecificationBuilder = ToolSpecification.builder();

        if (camelToolParameter != null) {
            toolSpecificationBuilder.description(camelToolParameter.getDescription());

            JsonObjectSchema.Builder parametersBuilder = JsonObjectSchema.builder();

            List<NamedJsonSchemaProperty> properties = camelToolParameter.getProperties();

            for (NamedJsonSchemaProperty namedProperty : properties) {
                parametersBuilder.addProperty(
                        namedProperty.getName(),
                        namedProperty.getProperties());
            }

            toolSpecificationBuilder.parameters(parametersBuilder.build());

        } else if (description != null) {
            toolSpecificationBuilder.description(description);

            if (parameters != null) {

                JsonObjectSchema.Builder parametersBuilder = JsonObjectSchema.builder();

                parameters.forEach((name, type) -> parametersBuilder.addProperty(name, createJsonSchema(type)));

                toolSpecificationBuilder.parameters(parametersBuilder.build());
            }
        } else {
            // Consumer without toolParameter or description
            throw new IllegalArgumentException(
                    "In order to use the langchain4j component as a consumer, you need to specify at least description, or a camelToolParameter");
        }

        final String toolName;

        if (name != null) {
            toolName = name;
        } else if (description != null) {
            toolName = StringHelper.dashToCamelCase(description.replace(" ", "-"));
        } else {
            toolName = null;
        }

        ToolSpecification toolSpecification = toolSpecificationBuilder
                .name(toolName)
                .build();

        final LangChain4jToolsConsumer langChain4jToolsConsumer = new LangChain4jToolsConsumer(this, processor);
        configureConsumer(langChain4jToolsConsumer);

        camelToolSpecification = new CamelToolSpecification(toolSpecification, langChain4jToolsConsumer, exposed);
        final CamelToolExecutorCache executorCache = CamelToolExecutorCache.getInstance();

        String[] splitTags = TagsHelper.splitTags(tags);
        for (String tag : splitTags) {
            if (exposed) {
                LOG.debug("Registering exposed tool: {} with tag: {}", toolSpecification.name(), tag);
                executorCache.put(tag, camelToolSpecification);
            } else {
                LOG.debug("Registering searchable tool: {} with tag: {}", toolSpecification.name(), tag);
                executorCache.putSearchable(tag, camelToolSpecification);
            }
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
    public LangChain4jToolsConfiguration getConfiguration() {
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

    public CamelSimpleToolParameter getCamelToolParameter() {
        return camelToolParameter;
    }

    public void setCamelToolParameter(CamelSimpleToolParameter camelToolParameter) {
        this.camelToolParameter = camelToolParameter;
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

    /**
     * Whether the tool is automatically exposed to the LLM
     *
     * @return true if the tool is exposed, false if searchable only
     */
    public boolean isExposed() {
        return exposed;
    }

    public void setExposed(boolean exposed) {
        this.exposed = exposed;
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();

        // Only remove tools registered by this endpoint, not all tools
        if (camelToolSpecification != null) {
            final CamelToolExecutorCache executorCache = CamelToolExecutorCache.getInstance();
            String[] splitTags = TagsHelper.splitTags(tags);

            for (String tag : splitTags) {
                if (exposed) {
                    LOG.debug("Removing exposed tool: {} with tag: {}",
                            camelToolSpecification.getToolSpecification().name(), tag);
                    executorCache.remove(tag, camelToolSpecification);
                } else {
                    LOG.debug("Removing searchable tool: {} with tag: {}",
                            camelToolSpecification.getToolSpecification().name(), tag);
                    executorCache.removeSearchable(tag, camelToolSpecification);
                }
            }
        }
    }

    /**
     * Creates a JsonScheùaElement based on a String type
     *
     * @param  type
     * @return
     */
    private JsonSchemaElement createJsonSchema(String type) {
        return switch (type.toLowerCase()) {
            case "string" -> JsonStringSchema.builder().build();
            case "integer" -> JsonIntegerSchema.builder().build();
            case "number" -> JsonNumberSchema.builder().build();
            case "boolean" -> JsonBooleanSchema.builder().build();
            default -> JsonStringSchema.builder().build(); // fallback for unkown types
        };
    }

}
