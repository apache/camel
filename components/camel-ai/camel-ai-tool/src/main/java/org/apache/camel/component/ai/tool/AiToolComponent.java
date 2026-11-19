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

import java.util.Map;
import java.util.stream.Collectors;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.DefaultComponent;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.PropertiesHelper;

import static org.apache.camel.component.ai.tool.AiTool.SCHEME;

/**
 * Camel component that registers routes as LLM-callable tools in the shared {@link AiToolRegistry}.
 *
 * @since 4.22
 */
@Component(SCHEME)
public class AiToolComponent extends DefaultComponent {

    @Metadata(description = "The component configuration")
    private AiToolConfiguration configuration;

    public AiToolComponent() {
        this(null);
    }

    public AiToolComponent(CamelContext context) {
        super(context);
        this.configuration = new AiToolConfiguration();
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        if (ObjectHelper.isEmpty(remaining)) {
            throw new IllegalArgumentException(
                    "A toolName must be provided: ai-tool:<toolName>?description=<desc>");
        }
        if (remaining.contains("/")) {
            throw new IllegalArgumentException(
                    "Tool name must not contain '/': ai-tool:<toolName>?description=<desc>");
        }

        final String toolName = remaining;

        // the configuration bean of the endpoint is taken out of the parameters before the options are bound, so the
        // options set on the endpoint win over the values of the bean, whatever the order they are given in, and the
        // values that the bean does not set come from the component (such as its authorization policy)
        AiToolConfiguration endpointConfiguration
                = resolveAndRemoveReferenceParameter(parameters, "configuration", AiToolConfiguration.class);
        AiToolConfiguration config = this.configuration.copy();
        if (endpointConfiguration != null) {
            config.overrideWith(endpointConfiguration);
        }

        Map<String, Object> toolParameters = PropertiesHelper.extractProperties(parameters, "parameter.");
        if (!toolParameters.isEmpty()) {
            config.setParameters(toolParameters.entrySet().stream()
                    .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().toString())));
        }

        Map<String, Object> outputToolParameters = PropertiesHelper.extractProperties(parameters, "outputParameter.");
        if (!outputToolParameters.isEmpty()) {
            config.setOutputParameters(outputToolParameters.entrySet().stream()
                    .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().toString())));
        }

        AiToolEndpoint endpoint = new AiToolEndpoint(uri, this, toolName, config);

        setProperties(endpoint, parameters);
        return endpoint;
    }

    public AiToolConfiguration getConfiguration() {
        return configuration;
    }

    /**
     * Sets the component configuration. The options already set on the component are kept, and win over the values of
     * the given configuration: when the configuration and the options are bound together (such as from
     * {@code camel.component.ai-tool.*} properties) the result does not depend on the order they are bound in, and an
     * option such as {@code authorizationPolicy} is never dropped.
     */
    public void setConfiguration(AiToolConfiguration configuration) {
        if (configuration != null && this.configuration != null && configuration != this.configuration) {
            configuration.overrideWith(this.configuration);
        }
        this.configuration = configuration;
    }
}
