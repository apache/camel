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

import java.util.Map;

import dev.langchain4j.agent.tool.JsonSchemaProperty;
import dev.langchain4j.agent.tool.ToolSpecification;
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

import static org.apache.camel.component.langchain4j.tools.LangChain4jTools.SCHEME;

@UriEndpoint(firstVersion = "4.8.0", scheme = SCHEME,
             title = "LangChain4j Tools",
             syntax = "langchain4j-tools:toolId",
             category = { Category.AI })
public class LangChain4jToolsEndpoint extends DefaultEndpoint {

    @Metadata(required = true)
    @UriPath(description = "The tool name")
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
    @UriParam(description = "List of Tool parameters in the form of parameter.<name>=<type>", prefix = "parameter.",
              multiValue = true)
    private Map<String, String> parameters;

    @Metadata(label = "consumer,advanced")
    @UriParam(description = "Tool's Camel Parameters, programmatically define Tool description and parameters")
    private CamelSimpleToolParameter camelToolParameter;

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

            for (NamedJsonSchemaProperty namedJsonSchemaProperty : camelToolParameter.getProperties()) {
                toolSpecificationBuilder.addParameter(namedJsonSchemaProperty.getName(),
                        namedJsonSchemaProperty.getProperties());
            }
        } else if (description != null) {
            toolSpecificationBuilder.description(description);

            if (parameters != null) {
                parameters.forEach((name, type) -> toolSpecificationBuilder.addParameter(name, JsonSchemaProperty.type(type)));
            }
        } else {
            // Consumer without toolParameter or description
            throw new IllegalArgumentException(
                    "In order to use the langchain4j component as a consumer, you need to specify at least description, or a camelToolParameter");
        }

        String simpleDescription = null;
        if (description != null) {
            simpleDescription = StringHelper.dashToCamelCase(description.replace(" ", "-"));
        }

        ToolSpecification toolSpecification = toolSpecificationBuilder
                .name(simpleDescription)
                .build();

        final LangChain4jToolsConsumer langChain4jToolsConsumer = new LangChain4jToolsConsumer(this, processor);
        configureConsumer(langChain4jToolsConsumer);

        CamelToolSpecification camelToolSpecification
                = new CamelToolSpecification(toolSpecification, langChain4jToolsConsumer);
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

    @Override
    protected void doStop() throws Exception {
        super.doStop();

        CamelToolExecutorCache.getInstance().getTools().clear();
    }
}
